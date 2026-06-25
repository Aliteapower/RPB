package com.rpb.reservation.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.common.scope.PlatformScope;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceTarget;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.application.port.out.StorePolicyRepositoryPort;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.DiningTableResourceRow;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationCreateApplicationServiceTest {

    @Test
    void createsConfirmedReservationWithExistingCustomer() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.reservationId()).isNotNull();
        assertThat(result.customerId()).isEqualTo(scenario.existingCustomer.id().value());
        assertThat(result.status()).isEqualTo("confirmed");
        assertThat(result.partySize()).isEqualTo(4);
        assertThat(result.reservedStartAt()).isEqualTo(scenario.startAt);
        assertThat(result.reservedEndAt()).isEqualTo(scenario.endAt);
        assertThat(result.holdUntilAt()).isEqualTo(scenario.startAt.plusSeconds(15 * 60L));
        assertThat(result.businessDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(scenario.reservationRepository.saved).hasSize(1);
        assertThat(scenario.reservationRepository.saved.getFirst().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(scenario.reservationRepository.saved.getFirst().customerId()).isEqualTo(scenario.existingCustomer.id());
        assertThat(scenario.businessEventRepository.events)
            .extracting(BusinessEvent::eventType)
            .containsExactly("reservation.created", "reservation.confirmed");
        assertThat(scenario.stateTransitionLogRepository.logs)
            .extracting(StateTransitionLog::transitionCode)
            .containsExactly("reservation.confirm");
        assertThat(scenario.auditLogRepository.logs)
            .extracting(AuditLog::operationCode)
            .contains("reservation.create");
        assertThat(scenario.idempotencyRepository.completed.getFirst().targetType()).isEqualTo("reservation");
    }

    @Test
    void createsTemporaryCustomerWithoutPhone() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithNoPhoneCustomer());

        assertThat(result.success()).isTrue();
        assertThat(scenario.customerRepository.saved).hasSize(1);
        assertThat(scenario.customerRepository.saved.getFirst().phone().isPresent()).isFalse();
        assertThat(scenario.reservationRepository.saved.getFirst().customerId())
            .isEqualTo(scenario.customerRepository.saved.getFirst().id());
    }

    @Test
    void createsCustomerFromPhoneWhenNoExistingCustomerMatches() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithPhoneCustomer("+6591234567"));

        assertThat(result.success()).isTrue();
        assertThat(scenario.customerRepository.saved).hasSize(1);
        assertThat(scenario.customerRepository.saved.getFirst().phone().value()).isEqualTo("+6591234567");
        assertThat(scenario.reservationRepository.saved.getFirst().customerId())
            .isEqualTo(scenario.customerRepository.saved.getFirst().id());
    }

    @Test
    void derivesReservedEndAtFromStorePolicyExpectedDiningDurationWhenMissing() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithoutEndAt());

        assertThat(result.success()).isTrue();
        assertThat(result.reservedEndAt()).isEqualTo(scenario.startAt.plusSeconds(90 * 60L));
        assertThat(scenario.reservationRepository.saved.getFirst().reservedEndAt())
            .isEqualTo(scenario.startAt.plusSeconds(90 * 60L));
    }

    @Test
    void createsActiveReservationPreassignmentWhenTableIsSelected() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithTable(scenario.tableA1.id()));

        assertThat(result.success()).isTrue();
        assertThat(scenario.preassignmentRepository.saved).hasSize(1);
        ReservationPreassignment preassignment = scenario.preassignmentRepository.saved.getFirst();
        assertThat(preassignment.reservationId().value()).isEqualTo(result.reservationId());
        assertThat(preassignment.resourceType()).isEqualTo("dining_table");
        assertThat(preassignment.resourceId()).isEqualTo(scenario.tableA1.id().value());
        assertThat(preassignment.status()).isEqualTo("active");
        assertThat(scenario.queueTicketCreated).isFalse();
        assertThat(scenario.seatingCreated).isFalse();
        assertThat(scenario.tableLockCreated).isFalse();
    }

    @Test
    void rejectsSelectedTableWhenAnotherActivePreassignmentOverlapsTheSlot() {
        Scenario scenario = Scenario.ready();
        scenario.preassignmentRepository.conflictingTargets.add(
            new ReservationResourceTarget("dining_table", scenario.tableA1.id().value())
        );

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithTable(scenario.tableA1.id()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.TABLE_NOT_AVAILABLE);
        assertThat(scenario.preassignmentRepository.saved).isEmpty();
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void rejectsSelectedTableWhenCapacityDoesNotFitPartySize() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithTable(scenario.smallTable.id()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.TABLE_CAPACITY_INSUFFICIENT);
        assertThat(scenario.preassignmentRepository.saved).isEmpty();
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void createsActiveReservationPreassignmentWhenTableGroupIsSelected() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithTableGroup(scenario.groupVip.id()));

        assertThat(result.success()).isTrue();
        assertThat(scenario.preassignmentRepository.saved).hasSize(1);
        ReservationPreassignment preassignment = scenario.preassignmentRepository.saved.getFirst();
        assertThat(preassignment.resourceType()).isEqualTo("table_group");
        assertThat(preassignment.resourceId()).isEqualTo(scenario.groupVip.id().value());
        assertThat(preassignment.status()).isEqualTo("active");
    }

    @Test
    void createsActiveReservationPreassignmentWhenTemporaryTableGroupIsSelected() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithTableGroup(scenario.temporaryGroup.id()));

        assertThat(result.success()).isTrue();
        assertThat(scenario.preassignmentRepository.saved).hasSize(1);
        ReservationPreassignment preassignment = scenario.preassignmentRepository.saved.getFirst();
        assertThat(preassignment.resourceType()).isEqualTo("table_group");
        assertThat(preassignment.resourceId()).isEqualTo(scenario.temporaryGroup.id().value());
        assertThat(preassignment.status()).isEqualTo("active");
    }

    @Test
    void rejectsCapacityUnavailableUsingV1FallbackCapacity() {
        Scenario scenario = Scenario.ready();
        scenario.reservationRepository.capacityUsage = 49;

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithPartySize(2));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.RESERVATION_CAPACITY_INSUFFICIENT);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void rejectsDuplicateActiveReservationForSameCustomerAndTimeRange() {
        Scenario scenario = Scenario.ready();
        scenario.reservationRepository.duplicateActive = true;

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.RESERVATION_DUPLICATE_ACTIVE);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void completedSameHashReplaysStoredReservationWithoutMutation() {
        Scenario scenario = Scenario.ready();
        String hash = ReservationCreateApplicationService.requestHash(scenario.commandWithExistingCustomer());
        UUID replayReservationId = UUID.randomUUID();
        scenario.idempotencyRepository.existing = completedRecord(
            hash,
            replayReservationId,
            scenario.existingCustomer.id().value(),
            "R-20260620-0007",
            4,
            scenario.startAt,
            scenario.endAt,
            scenario.startAt.plusSeconds(15 * 60L),
            LocalDate.of(2026, 6, 20)
        );

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.reservationId()).isEqualTo(replayReservationId);
        assertThat(result.reservationCode()).isEqualTo("R-20260620-0007");
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.ready();
        String hash = ReservationCreateApplicationService.requestHash(scenario.commandWithExistingCustomer());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-reservation-create"),
            "staff",
            "create_reservation",
            hash,
            IdempotencyStatus.STARTED,
            null,
            null,
            null
        );

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(ReservationCreateError.COMMAND_IN_PROGRESS);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.ready();
        String hash = ReservationCreateApplicationService.requestHash(scenario.commandWithExistingCustomer());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-reservation-create"),
            "staff",
            "create_reservation",
            hash,
            IdempotencyStatus.FAILED,
            null,
            null,
            "{\"failure_reason\":\"reservation_capacity_insufficient\"}"
        );

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.ready();
        scenario.idempotencyRepository.existing = completedRecord(
            "different-hash",
            UUID.randomUUID(),
            scenario.existingCustomer.id().value(),
            "R-20260620-0007",
            4,
            scenario.startAt,
            scenario.endAt,
            scenario.startAt.plusSeconds(15 * 60L),
            LocalDate.of(2026, 6, 20)
        );

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void invalidPhoneIsRejectedBeforePersistence() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithPhoneCustomer("91234567"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.INVALID_PHONE_E164);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void invalidPartySizeIsRejectedBeforePersistence() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithPartySize(0));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.INVALID_PARTY_SIZE);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void invalidTimeRangeIsRejectedBeforePersistence() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(
            scenario.commandWithRange(scenario.startAt, scenario.startAt.minusSeconds(60))
        );

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.INVALID_TIME_RANGE);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void pastStartTimeIsRejected() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithStartAt(scenario.now.minusSeconds(60)));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.RESERVATION_START_IN_PAST);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void customerNotFoundIsRejected() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithCustomerId(UUID.randomUUID()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.CUSTOMER_NOT_FOUND);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void reservationCodeConflictIsRejected() {
        Scenario scenario = Scenario.ready();
        scenario.reservationRepository.existingCodes.add("R-20260620-0007");

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithReservationCode("R-20260620-0007"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.RESERVATION_CODE_CONFLICT);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void businessEventWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready();
        scenario.businessEventRepository.failOnAppend = true;

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.BUSINESS_EVENT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void stateTransitionWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready();
        scenario.stateTransitionLogRepository.failOnAppend = true;

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.STATE_TRANSITION_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void auditWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready();
        scenario.auditLogRepository.failOnAppend = true;

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.AUDIT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void reservationSaveFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready();
        scenario.reservationRepository.failOnSave = true;

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.REPOSITORY_SAVE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void missingIdempotencyKeyIsRejectedBeforeStartingIdempotency() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithIdempotencyKey(null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCreateError.MISSING_IDEMPOTENCY_KEY);
        assertThat(scenario.idempotencyRepository.started).isEmpty();
    }

    @Test
    void boundaryDoesNotCreateQueueSeatingTableLockNoShowApiOrUi() {
        Scenario scenario = Scenario.ready();

        ReservationCreateResult result = scenario.service().createReservation(scenario.commandWithExistingCustomer());

        assertThat(result.success()).isTrue();
        assertThat(scenario.queueTicketCreated).isFalse();
        assertThat(scenario.seatingCreated).isFalse();
        assertThat(scenario.tableLockCreated).isFalse();
        assertThat(scenario.checkInCreated).isFalse();
        assertThat(scenario.noShowCreated).isFalse();
        assertThat(scenario.controllerCreated).isFalse();
        assertThat(scenario.apiDtoCreated).isFalse();
        assertThat(scenario.uiCreated).isFalse();
    }

    private static IdempotencyRecord completedRecord(
        String requestHash,
        UUID reservationId,
        UUID customerId,
        String reservationCode,
        int partySize,
        Instant reservedStartAt,
        Instant reservedEndAt,
        Instant holdUntilAt,
        LocalDate businessDate
    ) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-reservation-create"),
            "staff",
            "create_reservation",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "reservation",
            reservationId,
            """
                {"reservationId":"%s","customerId":"%s","reservationCode":"%s","partySize":%d,"businessDate":"%s","reservedStartAt":"%s","reservedEndAt":"%s","holdUntilAt":"%s","status":"confirmed"}
                """.formatted(reservationId, customerId, reservationCode, partySize, businessDate, reservedStartAt, reservedEndAt, holdUntilAt).trim()
        );
    }

    private static final class Scenario {
        final Instant now = Instant.parse("2026-06-19T02:00:00Z");
        final Instant startAt = Instant.parse("2026-06-20T03:00:00Z");
        final Instant endAt = Instant.parse("2026-06-20T04:30:00Z");
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "en-SG", "active");
        final StorePolicy policy = new StorePolicy(UUID.randomUUID(), scope, 15, 3, 90, "same_group_tail", "default");
        final DiningTable tableA1 = new DiningTable(
            new TableId(UUID.randomUUID()),
            scope,
            UUID.randomUUID(),
            "A01",
            new com.rpb.reservation.common.value.CapacityRange(2, 4),
            DiningTableStatus.AVAILABLE,
            true
        );
        final DiningTable smallTable = new DiningTable(
            new TableId(UUID.randomUUID()),
            scope,
            UUID.randomUUID(),
            "S01",
            new com.rpb.reservation.common.value.CapacityRange(1, 2),
            DiningTableStatus.AVAILABLE,
            true
        );
        final TableGroup groupVip = new TableGroup(
            new TableGroupId(UUID.randomUUID()),
            scope,
            "VIP-1",
            "fixed",
            new com.rpb.reservation.common.value.CapacityRange(6, 10),
            TableGroupStatus.ACTIVE
        );
        final TableGroup temporaryGroup = new TableGroup(
            new TableGroupId(UUID.randomUUID()),
            scope,
            "临组1",
            "temporary",
            new com.rpb.reservation.common.value.CapacityRange(6, 10),
            TableGroupStatus.CREATED
        );
        final Customer existingCustomer = new Customer(
            new CustomerId(UUID.randomUUID()),
            scope.tenantScope(),
            "C-EXISTING",
            "temporary",
            E164Phone.empty(),
            "active"
        );
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeStorePolicyRepository storePolicyRepository = new FakeStorePolicyRepository(this);
        final FakeCustomerRepository customerRepository = new FakeCustomerRepository(this);
        final FakeReservationRepository reservationRepository = new FakeReservationRepository();
        final FakeReservationPreassignmentRepository preassignmentRepository = new FakeReservationPreassignmentRepository();
        final FakeDiningTableRepository diningTableRepository = new FakeDiningTableRepository(this);
        final FakeTableGroupRepository tableGroupRepository = new FakeTableGroupRepository(this);
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean queueTicketCreated;
        boolean seatingCreated;
        boolean tableLockCreated;
        boolean checkInCreated;
        boolean noShowCreated;
        boolean controllerCreated;
        boolean apiDtoCreated;
        boolean uiCreated;

        static Scenario ready() {
            Scenario scenario = new Scenario();
            scenario.customerRepository.customers.put(scenario.existingCustomer.id().value(), scenario.existingCustomer);
            scenario.diningTableRepository.tables.put(scenario.tableA1.id(), scenario.tableA1);
            scenario.diningTableRepository.tables.put(scenario.smallTable.id(), scenario.smallTable);
            scenario.tableGroupRepository.groups.put(scenario.groupVip.id(), scenario.groupVip);
            scenario.tableGroupRepository.groups.put(scenario.temporaryGroup.id(), scenario.temporaryGroup);
            return scenario;
        }

        ReservationCreateApplicationService service() {
            return new ReservationCreateApplicationService(
                storeRepository,
                storePolicyRepository,
                customerRepository,
                reservationRepository,
                preassignmentRepository,
                diningTableRepository,
                tableGroupRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository,
                Clock.fixed(now, ZoneOffset.UTC),
                () -> 7
            );
        }

        CreateReservationCommand commandWithExistingCustomer() {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                4,
                startAt,
                endAt,
                existingCustomer.id().value(),
                null,
                null,
                null,
                "Window seat preferred",
                "idem-reservation-create",
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithNoPhoneCustomer() {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                2,
                startAt,
                endAt,
                null,
                "No Phone Guest",
                "VIP friend",
                null,
                null,
                "idem-no-phone",
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithPhoneCustomer(String phoneE164) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                2,
                startAt,
                endAt,
                null,
                "Phone Guest",
                null,
                phoneE164,
                null,
                "idem-phone-" + phoneE164,
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithoutEndAt() {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                3,
                startAt,
                null,
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                "idem-derived-end",
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithTable(TableId tableId) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                4,
                startAt,
                endAt,
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                "idem-table-" + tableId.value(),
                actorId,
                "staff",
                null,
                "staff",
                null,
                tableId.value(),
                null
            );
        }

        CreateReservationCommand commandWithTableGroup(TableGroupId tableGroupId) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                8,
                startAt,
                endAt,
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                "idem-table-group-" + tableGroupId.value(),
                actorId,
                "staff",
                null,
                "staff",
                null,
                null,
                tableGroupId.value()
            );
        }

        CreateReservationCommand commandWithPartySize(int partySize) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                partySize,
                startAt,
                endAt,
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                "idem-party-" + partySize,
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithStartAt(Instant reservedStartAt) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                2,
                reservedStartAt,
                reservedStartAt.plusSeconds(90 * 60L),
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                "idem-past",
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithRange(Instant reservedStartAt, Instant reservedEndAt) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                2,
                reservedStartAt,
                reservedEndAt,
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                "idem-range",
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithCustomerId(UUID customerId) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                2,
                startAt,
                endAt,
                customerId,
                null,
                null,
                null,
                null,
                "idem-customer-missing",
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithReservationCode(String reservationCode) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                2,
                startAt,
                endAt,
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                "idem-reservation-code",
                actorId,
                "staff",
                reservationCode,
                "staff",
                null
            );
        }

        CreateReservationCommand commandWithIdempotencyKey(String idempotencyKey) {
            return new CreateReservationCommand(
                tenantId.value(),
                storeId.value(),
                2,
                startAt,
                endAt,
                existingCustomer.id().value(),
                null,
                null,
                null,
                null,
                idempotencyKey,
                actorId,
                "staff",
                null,
                "staff",
                null
            );
        }
    }

    private static final class FakeStoreRepository implements StoreRepositoryPort {
        private final Scenario scenario;

        FakeStoreRepository(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<Store> findById(StoreScope scope) {
            return scenario.scope.equals(scope) ? Optional.of(scenario.store) : Optional.empty();
        }

        @Override
        public Optional<StorePolicy> findCurrentPolicy(StoreScope scope, OffsetDateTime at) {
            return scenario.scope.equals(scope) ? Optional.of(scenario.policy) : Optional.empty();
        }

        @Override
        public Store save(StoreScope scope, Store store) {
            return store;
        }

        @Override
        public StorePolicy savePolicy(StoreScope scope, StorePolicy policy) {
            return policy;
        }
    }

    private static final class FakeStorePolicyRepository implements StorePolicyRepositoryPort {
        private final Scenario scenario;

        FakeStorePolicyRepository(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<StorePolicy> findByStoreScope(StoreScope scope) {
            return scenario.scope.equals(scope) ? Optional.of(scenario.policy) : Optional.empty();
        }
    }

    private static final class FakeDiningTableRepository implements DiningTableRepositoryPort {
        private final Map<TableId, DiningTable> tables = new HashMap<>();
        private final Scenario scenario;

        private FakeDiningTableRepository(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<DiningTable> findById(StoreScope scope, TableId tableId) {
            return Optional.ofNullable(tables.get(tableId)).filter(table -> table.scope().equals(scope));
        }

        @Override
        public List<DiningTable> findActiveByArea(StoreScope scope, UUID areaId) {
            return tables.values().stream()
                .filter(table -> table.scope().equals(scope))
                .filter(table -> table.areaId().equals(areaId))
                .toList();
        }

        @Override
        public List<DiningTable> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return tables.values().stream()
                .filter(table -> table.scope().equals(scope))
                .filter(table -> table.capacity().includes(partySize))
                .toList();
        }

        @Override
        public List<DiningTable> findVisibleResources(StoreScope scope, String status, PartySize partySize) {
            return findCandidates(scope, partySize == null ? new PartySize(1) : partySize, new BusinessDate(LocalDate.of(2026, 6, 20)));
        }

        @Override
        public List<DiningTableResourceRow> findVisibleResourceRows(StoreScope scope, String status, PartySize partySize) {
            return tables.values().stream()
                .filter(table -> table.scope().equals(scope))
                .map(table -> new DiningTableResourceRow(
                    table.id().value(),
                    table.tableCode(),
                    table.tableCode(),
                    "A区",
                    table.capacity().min(),
                    table.capacity().max(),
                    table.status().code()
                ))
                .toList();
        }

        @Override
        public DiningTable save(StoreScope scope, DiningTable table) {
            assertThat(scope).isEqualTo(scenario.scope);
            tables.put(table.id(), table);
            return table;
        }
    }

    private static final class FakeTableGroupRepository implements TableGroupRepositoryPort {
        private final Map<TableGroupId, TableGroup> groups = new HashMap<>();
        private final Scenario scenario;

        private FakeTableGroupRepository(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId) {
            return Optional.ofNullable(groups.get(tableGroupId)).filter(group -> group.scope().equals(scope));
        }

        @Override
        public List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId) {
            return List.of();
        }

        @Override
        public List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId) {
            return List.of();
        }

        @Override
        public List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return groups.values().stream()
                .filter(group -> group.scope().equals(scope))
                .filter(group -> group.capacity().includes(partySize))
                .toList();
        }

        @Override
        public List<TableGroup> findVisibleGroups(StoreScope scope, String status, PartySize partySize) {
            return findCandidates(scope, partySize == null ? new PartySize(1) : partySize, new BusinessDate(LocalDate.of(2026, 6, 20)));
        }

        @Override
        public TableGroup save(StoreScope scope, TableGroup tableGroup) {
            assertThat(scope).isEqualTo(scenario.scope);
            groups.put(tableGroup.id(), tableGroup);
            return tableGroup;
        }

        @Override
        public TableGroupMember saveMember(StoreScope scope, TableGroupMember member) {
            return member;
        }
    }

    private static final class FakeReservationPreassignmentRepository implements ReservationPreassignmentRepositoryPort {
        final List<ReservationPreassignment> saved = new ArrayList<>();
        final Set<ReservationResourceTarget> conflictingTargets = new HashSet<>();

        @Override
        public boolean existsActiveResourceConflict(
            StoreScope scope,
            String resourceType,
            UUID resourceId,
            BusinessDate businessDate,
            TimeRange timeRange
        ) {
            return conflictingTargets.contains(new ReservationResourceTarget(resourceType, resourceId));
        }

        @Override
        public Set<ReservationResourceTarget> findActiveResourceTargetsForDate(StoreScope scope, BusinessDate businessDate) {
            return conflictingTargets;
        }

        @Override
        public Set<ReservationResourceAssignment> findActiveResourceAssignmentsForDate(StoreScope scope, BusinessDate businessDate) {
            return conflictingTargets.stream()
                .map(target -> new ReservationResourceAssignment(
                    UUID.randomUUID(),
                    "R-CONFLICT",
                    "confirmed",
                    4,
                    null,
                    null,
                    null,
                    null,
                    target.resourceType(),
                    target.resourceId(),
                    null,
                    null,
                    null,
                    null
                ))
                .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public ReservationPreassignment save(StoreScope scope, ReservationPreassignment preassignment) {
            saved.add(preassignment);
            return preassignment;
        }
    }

    private static final class FakeCustomerRepository implements CustomerRepositoryPort {
        final Map<UUID, Customer> customers = new HashMap<>();
        final List<Customer> saved = new ArrayList<>();
        private final Scenario scenario;

        FakeCustomerRepository(Scenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Optional<Customer> findById(TenantScope scope, CustomerId customerId) {
            return Optional.ofNullable(customers.get(customerId.value())).filter(customer -> customer.scope().equals(scope));
        }

        @Override
        public Optional<Customer> findByCode(TenantScope scope, String customerCode) {
            return customers.values().stream()
                .filter(customer -> customer.scope().equals(scope) && customer.customerCode().equals(customerCode))
                .findFirst();
        }

        @Override
        public Optional<Customer> findByPhone(TenantScope scope, E164Phone phone) {
            return customers.values().stream()
                .filter(customer -> customer.scope().equals(scope) && customer.phone().equals(phone))
                .findFirst();
        }

        @Override
        public List<Customer> searchNoPhoneCandidates(TenantScope scope, String lookupText) {
            return List.of();
        }

        @Override
        public Customer save(TenantScope scope, Customer customer) {
            assertThat(scope).isEqualTo(scenario.scope.tenantScope());
            saved.add(customer);
            customers.put(customer.id().value(), customer);
            return customer;
        }
    }

    private static final class FakeReservationRepository implements ReservationRepositoryPort {
        final List<Reservation> saved = new ArrayList<>();
        final List<String> existingCodes = new ArrayList<>();
        int capacityUsage;
        boolean duplicateActive;
        boolean failOnSave;

        @Override
        public Optional<Reservation> findById(StoreScope scope, ReservationId reservationId) {
            return saved.stream().filter(reservation -> reservation.id().equals(reservationId)).findFirst();
        }

        @Override
        public Optional<Reservation> findByCode(StoreScope scope, ReservationCode reservationCode) {
            return saved.stream().filter(reservation -> reservation.reservationCode().equals(reservationCode)).findFirst();
        }

        @Override
        public boolean existsByReservationCode(StoreScope scope, ReservationCode reservationCode) {
            return existingCodes.contains(reservationCode.value())
                || saved.stream().anyMatch(reservation -> reservation.reservationCode().equals(reservationCode));
        }

        @Override
        public List<Reservation> findStoreSchedule(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
            return saved.stream()
                .filter(reservation -> reservation.scope().equals(scope) && reservation.businessDate().equals(businessDate))
                .toList();
        }

        @Override
        public boolean existsActiveDuplicate(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
            return duplicateActive;
        }

        @Override
        public List<Reservation> findActiveConflicts(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
            return duplicateActive ? saved : List.of();
        }

        @Override
        public int findActiveCapacityUsage(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
            return capacityUsage;
        }

        @Override
        public Reservation save(StoreScope scope, Reservation reservation) {
            if (failOnSave) {
                throw new IllegalStateException("reservation save failed");
            }
            saved.add(reservation);
            return reservation;
        }
    }

    private static final class FakeBusinessEventRepository implements BusinessEventRepositoryPort {
        final List<BusinessEvent> events = new ArrayList<>();
        boolean failOnAppend;

        @Override
        public BusinessEvent append(StoreScope scope, BusinessEvent event) {
            if (failOnAppend) {
                throw new IllegalStateException("business event failed");
            }
            events.add(event);
            return event;
        }

        @Override
        public BusinessEvent append(TenantScope scope, BusinessEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public BusinessEvent append(PlatformScope scope, BusinessEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public List<BusinessEvent> findByTarget(StoreScope scope, String targetType, UUID targetId) {
            return events.stream().filter(event -> event.targetType().equals(targetType) && event.targetId().equals(targetId)).toList();
        }

        @Override
        public List<BusinessEvent> findTimeline(StoreScope scope, TimeRange timeRange) {
            return events;
        }
    }

    private static final class FakeStateTransitionLogRepository implements StateTransitionLogRepositoryPort {
        final List<StateTransitionLog> logs = new ArrayList<>();
        boolean failOnAppend;

        @Override
        public StateTransitionLog append(StoreScope scope, StateTransitionLog transitionLog) {
            if (failOnAppend) {
                throw new IllegalStateException("state transition failed");
            }
            logs.add(transitionLog);
            return transitionLog;
        }

        @Override
        public StateTransitionLog append(TenantScope scope, StateTransitionLog transitionLog) {
            logs.add(transitionLog);
            return transitionLog;
        }

        @Override
        public StateTransitionLog append(PlatformScope scope, StateTransitionLog transitionLog) {
            logs.add(transitionLog);
            return transitionLog;
        }

        @Override
        public List<StateTransitionLog> findByTarget(StoreScope scope, String targetType, UUID targetId) {
            return logs.stream().filter(log -> log.targetType().equals(targetType) && log.targetId().equals(targetId)).toList();
        }

        @Override
        public Optional<StateTransitionLog> findLatest(StoreScope scope, String targetType, UUID targetId) {
            return logs.stream().filter(log -> log.targetType().equals(targetType) && log.targetId().equals(targetId)).reduce((first, second) -> second);
        }
    }

    private static final class FakeAuditLogRepository implements AuditLogRepositoryPort {
        boolean failOnAppend;
        final List<AuditLog> logs = new ArrayList<>();

        @Override
        public AuditLog append(StoreScope scope, AuditLog auditLog) {
            if (failOnAppend) {
                throw new IllegalStateException("audit failed");
            }
            logs.add(auditLog);
            return auditLog;
        }

        @Override
        public AuditLog append(TenantScope scope, AuditLog auditLog) {
            logs.add(auditLog);
            return auditLog;
        }

        @Override
        public AuditLog append(PlatformScope scope, AuditLog auditLog) {
            logs.add(auditLog);
            return auditLog;
        }

        @Override
        public List<AuditLog> findByTarget(StoreScope scope, String targetType, UUID targetId) {
            return logs.stream().filter(log -> log.targetType().equals(targetType) && targetId.equals(log.targetId())).toList();
        }

        @Override
        public List<AuditLog> findByOperation(StoreScope scope, String operationCode, TimeRange timeRange) {
            return logs.stream().filter(log -> log.operationCode().equals(operationCode)).toList();
        }
    }

    private static final class FakeIdempotencyRepository implements IdempotencyRepositoryPort {
        IdempotencyRecord existing;
        final List<IdempotencyRecord> started = new ArrayList<>();
        final List<IdempotencyRecord> completed = new ArrayList<>();
        final List<IdempotencyRecord> failed = new ArrayList<>();

        @Override
        public Optional<IdempotencyRecord> findByScopeActionKey(StoreScope scope, String source, String action, IdempotencyKey key) {
            return Optional.ofNullable(existing);
        }

        @Override
        public Optional<IdempotencyRecord> findByScopeActionKey(TenantScope scope, String source, String action, IdempotencyKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<IdempotencyRecord> findByScopeActionKey(PlatformScope scope, String source, String action, IdempotencyKey key) {
            return Optional.empty();
        }

        @Override
        public IdempotencyRecord start(StoreScope scope, String source, String action, IdempotencyKey key, String requestHash, OffsetDateTime expiresAt) {
            IdempotencyRecord record = new IdempotencyRecord(
                UUID.randomUUID(),
                key,
                source,
                action,
                requestHash,
                IdempotencyStatus.STARTED,
                null,
                null,
                null
            );
            started.add(record);
            existing = record;
            return record;
        }

        @Override
        public IdempotencyRecord complete(StoreScope scope, IdempotencyRecord record, String targetType) {
            IdempotencyRecord completedRecord = new IdempotencyRecord(
                record.id(),
                record.idempotencyKey(),
                record.source(),
                record.action(),
                record.requestHash(),
                IdempotencyStatus.COMPLETED,
                targetType,
                record.targetId(),
                record.responseSnapshot()
            );
            completed.add(completedRecord);
            existing = completedRecord;
            return completedRecord;
        }

        @Override
        public IdempotencyRecord fail(StoreScope scope, IdempotencyRecord record, String failureReason) {
            IdempotencyRecord failedRecord = new IdempotencyRecord(
                record.id(),
                record.idempotencyKey(),
                record.source(),
                record.action(),
                record.requestHash(),
                IdempotencyStatus.FAILED,
                record.targetType(),
                record.targetId(),
                "{\"failure_reason\":\"" + failureReason + "\"}"
            );
            failed.add(failedRecord);
            existing = failedRecord;
            return failedRecord;
        }
    }
}
