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
import com.rpb.reservation.common.value.CapacityRange;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.command.SeatArrivedReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.domain.TableLock;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationArrivedDirectSeatingApplicationServiceTest {

    @Test
    void seatsArrivedReservationToSingleTableAndWritesEvidenceAndBoundaries() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.reservationId()).isEqualTo(scenario.reservationId.value());
        assertThat(result.reservationCode()).isEqualTo("R-SEAT-1");
        assertThat(result.reservationStatus()).isEqualTo("seated");
        assertThat(result.seatingId()).isNotNull();
        assertThat(result.resourceType()).isEqualTo("dining_table");
        assertThat(result.resourceId()).isEqualTo(scenario.table.id().value());
        assertThat(result.partySizeSnapshot()).isEqualTo(4);
        assertThat(result.seatingStatus()).isEqualTo("occupied");
        assertThat(result.seatingResourceStatus()).isEqualTo("active");
        assertThat(result.tableStatus()).isEqualTo("occupied");
        assertThat(result.alreadySeated()).isFalse();
        assertThat(result.idempotencyStatus()).isEqualTo("completed");
        assertThat(result.events()).containsExactly("reservation.seated", "seating.created", "table.occupied");

        assertThat(scenario.reservationRepository.saved).hasSize(1);
        assertThat(scenario.reservationRepository.saved.getFirst().status()).isEqualTo(ReservationStatus.SEATED);
        assertThat(scenario.reservationRepository.saved.getFirst().updatedAt()).isEqualTo(scenario.now);
        assertThat(scenario.seatingRepository.saved).hasSize(1);
        assertThat(scenario.seatingRepository.saved.getFirst().sourceType()).isEqualTo("reservation");
        assertThat(scenario.seatingRepository.saved.getFirst().sourceId()).isEqualTo(scenario.reservationId.value());
        assertThat(scenario.seatingRepository.saved.getFirst().status()).isEqualTo(SeatingStatus.OCCUPIED);
        assertThat(scenario.seatingRepository.savedResources).hasSize(1);
        assertThat(scenario.seatingRepository.savedResources.getFirst().status()).isEqualTo("active");
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status).contains(DiningTableStatus.OCCUPIED);
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .containsExactly("reservation.seated", "seating.created", "table.occupied");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::transitionCode)
            .contains("reservation.seat", "seating.occupy", "dining_table.occupy");
        assertThat(scenario.stateTransitionLogRepository.logs)
            .anySatisfy(log -> {
                assertThat(log.targetType()).isEqualTo("reservation");
                assertThat(log.fromStatus()).isEqualTo("arrived");
                assertThat(log.toStatus()).isEqualTo("seated");
            });
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.seat");
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);

        assertThat(scenario.queueTicketCreated).isFalse();
        assertThat(scenario.cleaningCreated).isFalse();
        assertThat(scenario.turnoverCreated).isFalse();
        assertThat(scenario.noShowImplemented).isFalse();
        assertThat(scenario.cancellationImplemented).isFalse();
        assertThat(scenario.controllerCreated).isFalse();
        assertThat(scenario.apiDtoCreated).isFalse();
        assertThat(scenario.uiCreated).isFalse();
        assertThat(scenario.migrationChanged).isFalse();
    }

    @Test
    void seatsArrivedReservationToTableGroupAndOccupiesEveryMemberTable() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithGroup(scenario.group.id().value()));

        assertThat(result.success()).isTrue();
        assertThat(result.resourceType()).isEqualTo("table_group");
        assertThat(result.resourceId()).isEqualTo(scenario.group.id().value());
        assertThat(result.groupMemberStatuses()).containsExactly("occupied", "occupied");
        assertThat(result.occupiedTableIds()).containsExactlyInAnyOrder(
            scenario.table.id().value(),
            scenario.groupMemberTable.id().value()
        );
        assertThat(scenario.seatingRepository.savedResources.getFirst().resourceType()).isEqualTo("table_group");
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::id)
            .containsExactlyInAnyOrder(scenario.table.id(), scenario.groupMemberTable.id());
        assertThat(scenario.diningTableRepository.saved).extracting(DiningTable::status)
            .containsOnly(DiningTableStatus.OCCUPIED);
        assertThat(scenario.tableGroupRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events)
            .anySatisfy(event -> {
                assertThat(event.eventType()).isEqualTo("table.occupied");
                assertThat(event.targetType()).isEqualTo("table_group");
                assertThat(event.targetId()).isEqualTo(scenario.group.id().value());
            });
        assertThat(scenario.stateTransitionLogRepository.logs)
            .filteredOn(log -> log.targetType().equals("dining_table") && log.toStatus().equals("occupied"))
            .hasSize(2);
    }

    @Test
    void rejectsSeatingWhenReservationBusinessDateIsNotStoreToday() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.replaceReservationBusinessDate(LocalDate.of(2026, 6, 21));

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedDirectSeatingError.RESERVATION_NOT_TODAY);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.savedResources).isEmpty();
        assertThat(scenario.diningTableRepository.saved).isEmpty();
        assertThat(scenario.diningTableRepository.tables.get(scenario.table.id().value()).status()).isEqualTo(DiningTableStatus.AVAILABLE);
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.seat.failed");
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutMutation() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        SeatArrivedReservationCommand command = scenario.commandWithTable(scenario.table.id().value());
        String hash = ReservationArrivedDirectSeatingApplicationService.requestHash(command);
        UUID replaySeatingId = UUID.randomUUID();
        scenario.idempotencyRepository.existing = completedRecord(
            hash,
            scenario.reservationId.value(),
            replaySeatingId,
            scenario.table.id().value(),
            false
        );

        ReservationArrivedDirectSeatingResult result = scenario.service().seatArrivedReservation(command);

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.seatingId()).isEqualTo(replaySeatingId);
        assertThat(result.resourceId()).isEqualTo(scenario.table.id().value());
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.savedResources).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        SeatArrivedReservationCommand command = scenario.commandWithTable(scenario.table.id().value());
        String hash = ReservationArrivedDirectSeatingApplicationService.requestHash(command);
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.STARTED, null);

        ReservationArrivedDirectSeatingResult result = scenario.service().seatArrivedReservation(command);

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(ReservationArrivedDirectSeatingError.COMMAND_IN_PROGRESS);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        SeatArrivedReservationCommand command = scenario.commandWithTable(scenario.table.id().value());
        String hash = ReservationArrivedDirectSeatingApplicationService.requestHash(command);
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.FAILED, "{\"failure_reason\":\"audit_write_failed\"}");

        ReservationArrivedDirectSeatingResult result = scenario.service().seatArrivedReservation(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedDirectSeatingError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.idempotencyRepository.existing = completedRecord(
            "different-hash",
            scenario.reservationId.value(),
            UUID.randomUUID(),
            scenario.table.id().value(),
            false
        );

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedDirectSeatingError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void alreadySeatedWithMatchingActiveSeatingReturnsAlreadySeatedWithoutDuplicateEvidence() {
        Scenario scenario = Scenario.ready(ReservationStatus.SEATED);
        Seating existingSeating = scenario.persistExistingReservationSeating(scenario.table.id().value(), "dining_table");

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithTableAndKey(scenario.table.id().value(), "idem-already-seated"));

        assertThat(result.success()).isTrue();
        assertThat(result.alreadySeated()).isTrue();
        assertThat(result.replayed()).isFalse();
        assertThat(result.seatingId()).isEqualTo(existingSeating.id().value());
        assertThat(result.resourceId()).isEqualTo(scenario.table.id().value());
        assertThat(result.reservationStatus()).isEqualTo("seated");
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.savedResources).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
    }

    @Test
    void alreadySeatedWithoutMatchingActiveSeatingIsConsistencyError() {
        Scenario scenario = Scenario.ready(ReservationStatus.SEATED);

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithTableAndKey(scenario.table.id().value(), "idem-seated-without-seating"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedDirectSeatingError.RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING);
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.seat.failed");
    }

    @Test
    void resourceSelectionMustBeExactlyOneTableOrGroup() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);

        ReservationArrivedDirectSeatingResult both = scenario.service().seatArrivedReservation(
            scenario.command(scenario.table.id().value(), scenario.group.id().value(), "idem-both")
        );
        ReservationArrivedDirectSeatingResult neither = scenario.service().seatArrivedReservation(
            scenario.command(null, null, "idem-neither")
        );

        assertThat(both.success()).isFalse();
        assertThat(both.error()).isEqualTo(ReservationArrivedDirectSeatingError.RESOURCE_SELECTION_CONFLICT);
        assertThat(neither.success()).isFalse();
        assertThat(neither.error()).isEqualTo(ReservationArrivedDirectSeatingError.RESOURCE_SELECTION_REQUIRED);
        assertThat(scenario.idempotencyRepository.started).isEmpty();
    }

    @Test
    void reservationStatusFailuresReturnApplicationErrors() {
        assertStatusRejected(ReservationStatus.CONFIRMED, ReservationArrivedDirectSeatingError.RESERVATION_STATUS_NOT_ARRIVED);
        assertStatusRejected(ReservationStatus.CANCELLED, ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_CANCELLED);
        assertStatusRejected(ReservationStatus.NO_SHOW, ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_NO_SHOW);
        assertStatusRejected(ReservationStatus.COMPLETED, ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_COMPLETED);
    }

    @Test
    void reservationNotFoundReturnsApplicationError() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.reservationRepository.reservations.clear();

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedDirectSeatingError.RESERVATION_NOT_FOUND);
        assertThat(scenario.seatingRepository.saved).isEmpty();
    }

    @Test
    void tableFailuresReturnApplicationErrors() {
        Scenario notFound = Scenario.ready(ReservationStatus.ARRIVED);
        ReservationArrivedDirectSeatingResult missing = notFound.service()
            .seatArrivedReservation(notFound.commandWithTable(UUID.randomUUID()));
        assertThat(missing.success()).isFalse();
        assertThat(missing.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_NOT_FOUND);

        Scenario unavailable = Scenario.ready(ReservationStatus.ARRIVED);
        unavailable.replaceTableStatus(DiningTableStatus.OCCUPIED);
        ReservationArrivedDirectSeatingResult occupied = unavailable.service()
            .seatArrivedReservation(unavailable.commandWithTable(unavailable.table.id().value()));
        assertThat(occupied.success()).isFalse();
        assertThat(occupied.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_NOT_AVAILABLE);

        Scenario insufficient = Scenario.ready(ReservationStatus.ARRIVED);
        insufficient.replaceTableCapacity(new CapacityRange(1, 2));
        ReservationArrivedDirectSeatingResult tooSmall = insufficient.service()
            .seatArrivedReservation(insufficient.commandWithTable(insufficient.table.id().value()));
        assertThat(tooSmall.success()).isFalse();
        assertThat(tooSmall.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_CAPACITY_INSUFFICIENT);

        Scenario locked = Scenario.ready(ReservationStatus.ARRIVED);
        locked.tableLockRepository.conflict = true;
        ReservationArrivedDirectSeatingResult conflict = locked.service()
            .seatArrivedReservation(locked.commandWithTable(locked.table.id().value()));
        assertThat(conflict.success()).isFalse();
        assertThat(conflict.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_LOCK_CONFLICT);
    }

    @Test
    void tableGroupFailuresReturnApplicationErrors() {
        Scenario notFound = Scenario.ready(ReservationStatus.ARRIVED);
        ReservationArrivedDirectSeatingResult missing = notFound.service()
            .seatArrivedReservation(notFound.commandWithGroup(UUID.randomUUID()));
        assertThat(missing.success()).isFalse();
        assertThat(missing.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_GROUP_NOT_FOUND);

        Scenario invalid = Scenario.ready(ReservationStatus.ARRIVED);
        invalid.replaceGroupStatus(TableGroupStatus.INACTIVE);
        ReservationArrivedDirectSeatingResult inactive = invalid.service()
            .seatArrivedReservation(invalid.commandWithGroup(invalid.group.id().value()));
        assertThat(inactive.success()).isFalse();
        assertThat(inactive.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_GROUP_INVALID);

        Scenario memberUnavailable = Scenario.ready(ReservationStatus.ARRIVED);
        memberUnavailable.replaceGroupMemberStatus(DiningTableStatus.CLEANING);
        ReservationArrivedDirectSeatingResult cleaningMember = memberUnavailable.service()
            .seatArrivedReservation(memberUnavailable.commandWithGroup(memberUnavailable.group.id().value()));
        assertThat(cleaningMember.success()).isFalse();
        assertThat(cleaningMember.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE);

        Scenario insufficient = Scenario.ready(ReservationStatus.ARRIVED);
        insufficient.replaceGroupCapacity(new CapacityRange(1, 2));
        ReservationArrivedDirectSeatingResult tooSmall = insufficient.service()
            .seatArrivedReservation(insufficient.commandWithGroup(insufficient.group.id().value()));
        assertThat(tooSmall.success()).isFalse();
        assertThat(tooSmall.error()).isEqualTo(ReservationArrivedDirectSeatingError.TABLE_GROUP_CAPACITY_INSUFFICIENT);
    }

    @Test
    void eventTransitionAuditAndPersistenceFailuresReturnApplicationErrorsAndMarkIdempotencyFailed() {
        Scenario eventFailure = Scenario.ready(ReservationStatus.ARRIVED);
        eventFailure.businessEventRepository.failOnAppend = true;
        ReservationArrivedDirectSeatingResult eventResult = eventFailure.service()
            .seatArrivedReservation(eventFailure.commandWithTable(eventFailure.table.id().value()));
        assertThat(eventResult.success()).isFalse();
        assertThat(eventResult.error()).isEqualTo(ReservationArrivedDirectSeatingError.BUSINESS_EVENT_WRITE_FAILED);
        assertThat(eventFailure.idempotencyRepository.failed).hasSize(1);

        Scenario transitionFailure = Scenario.ready(ReservationStatus.ARRIVED);
        transitionFailure.stateTransitionLogRepository.failOnAppend = true;
        ReservationArrivedDirectSeatingResult transitionResult = transitionFailure.service()
            .seatArrivedReservation(transitionFailure.commandWithTableAndKey(transitionFailure.table.id().value(), "idem-transition"));
        assertThat(transitionResult.success()).isFalse();
        assertThat(transitionResult.error()).isEqualTo(ReservationArrivedDirectSeatingError.STATE_TRANSITION_WRITE_FAILED);
        assertThat(transitionFailure.idempotencyRepository.failed).hasSize(1);

        Scenario auditFailure = Scenario.ready(ReservationStatus.ARRIVED);
        auditFailure.auditLogRepository.failOnAppend = true;
        ReservationArrivedDirectSeatingResult auditResult = auditFailure.service()
            .seatArrivedReservation(auditFailure.commandWithTableAndKey(auditFailure.table.id().value(), "idem-audit"));
        assertThat(auditResult.success()).isFalse();
        assertThat(auditResult.error()).isEqualTo(ReservationArrivedDirectSeatingError.AUDIT_WRITE_FAILED);
        assertThat(auditFailure.idempotencyRepository.failed).hasSize(1);

        Scenario persistenceFailure = Scenario.ready(ReservationStatus.ARRIVED);
        persistenceFailure.seatingRepository.failOnSave = true;
        ReservationArrivedDirectSeatingResult persistenceResult = persistenceFailure.service()
            .seatArrivedReservation(persistenceFailure.commandWithTableAndKey(persistenceFailure.table.id().value(), "idem-persistence"));
        assertThat(persistenceResult.success()).isFalse();
        assertThat(persistenceResult.error()).isEqualTo(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED);
        assertThat(persistenceFailure.idempotencyRepository.failed).hasSize(1);
    }

    private static void assertStatusRejected(ReservationStatus status, ReservationArrivedDirectSeatingError expectedError) {
        Scenario scenario = Scenario.ready(status);

        ReservationArrivedDirectSeatingResult result = scenario.service()
            .seatArrivedReservation(scenario.commandWithTable(scenario.table.id().value()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(expectedError);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.seatingRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.seat.failed");
    }

    private static IdempotencyRecord completedRecord(
        String requestHash,
        UUID reservationId,
        UUID seatingId,
        UUID resourceId,
        boolean alreadySeated
    ) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-seat-table"),
            "staff",
            "seat_arrived_reservation",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "seating",
            seatingId,
            """
                {"reservationId":"%s","reservationCode":"R-SEAT-1","reservationStatus":"seated","seatingId":"%s","resourceType":"dining_table","resourceId":"%s","partySizeSnapshot":4,"seatingStatus":"occupied","seatingResourceStatus":"active","tableStatus":"occupied","groupMemberStatuses":[],"alreadySeated":%s}
                """.formatted(reservationId, seatingId, resourceId, alreadySeated).trim()
        );
    }

    private static IdempotencyRecord idempotencyRecord(String requestHash, IdempotencyStatus status, String snapshot) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-seat-table"),
            "staff",
            "seat_arrived_reservation",
            requestHash,
            status,
            null,
            null,
            snapshot
        );
    }

    private static final class Scenario {
        final Instant now = Instant.parse("2026-06-20T05:00:00Z");
        final Instant reservedStartAt = Instant.parse("2026-06-20T06:00:00Z");
        final Instant reservedEndAt = Instant.parse("2026-06-20T07:30:00Z");
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final UUID areaId = UUID.randomUUID();
        final ReservationId reservationId = new ReservationId(UUID.randomUUID());
        final CustomerId customerId = new CustomerId(UUID.randomUUID());
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "en-SG", "active");
        final DiningTable table = table("T-01", new CapacityRange(1, 4), DiningTableStatus.AVAILABLE);
        final DiningTable groupMemberTable = table("T-02", new CapacityRange(1, 4), DiningTableStatus.AVAILABLE);
        final TableGroup group = new TableGroup(
            new TableGroupId(UUID.randomUUID()),
            scope,
            "G-01",
            "fixed",
            new CapacityRange(3, 8),
            TableGroupStatus.ACTIVE
        );
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeReservationRepository reservationRepository = new FakeReservationRepository();
        final FakeDiningTableRepository diningTableRepository = new FakeDiningTableRepository();
        final FakeTableGroupRepository tableGroupRepository = new FakeTableGroupRepository();
        final FakeTableLockRepository tableLockRepository = new FakeTableLockRepository();
        final FakeSeatingRepository seatingRepository = new FakeSeatingRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean queueTicketCreated;
        boolean cleaningCreated;
        boolean turnoverCreated;
        boolean noShowImplemented;
        boolean cancellationImplemented;
        boolean controllerCreated;
        boolean apiDtoCreated;
        boolean uiCreated;
        boolean migrationChanged;

        static Scenario ready(ReservationStatus status) {
            Scenario scenario = new Scenario();
            scenario.reservationRepository.reservations.put(scenario.reservationId.value(), scenario.reservation(status));
            scenario.diningTableRepository.tables.put(scenario.table.id().value(), scenario.table);
            scenario.diningTableRepository.tables.put(scenario.groupMemberTable.id().value(), scenario.groupMemberTable);
            scenario.tableGroupRepository.groups.put(scenario.group.id().value(), scenario.group);
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.group.id(),
                scenario.table.id(),
                "primary"
            ));
            scenario.tableGroupRepository.members.add(new TableGroupMember(
                UUID.randomUUID(),
                scenario.scope,
                scenario.group.id(),
                scenario.groupMemberTable.id(),
                "secondary"
            ));
            return scenario;
        }

        ReservationArrivedDirectSeatingApplicationService service() {
            return new ReservationArrivedDirectSeatingApplicationService(
                storeRepository,
                reservationRepository,
                diningTableRepository,
                tableGroupRepository,
                tableLockRepository,
                seatingRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository,
                Clock.fixed(now, ZoneOffset.UTC)
            );
        }

        SeatArrivedReservationCommand commandWithTable(UUID tableId) {
            return commandWithTableAndKey(tableId, "idem-seat-table");
        }

        SeatArrivedReservationCommand commandWithTableAndKey(UUID tableId, String key) {
            return command(tableId, null, key);
        }

        SeatArrivedReservationCommand commandWithGroup(UUID tableGroupId) {
            return command(null, tableGroupId, "idem-seat-group");
        }

        SeatArrivedReservationCommand command(UUID tableId, UUID tableGroupId, String key) {
            return new SeatArrivedReservationCommand(
                tenantId.value(),
                storeId.value(),
                reservationId.value(),
                tableId,
                tableGroupId,
                key,
                actorId,
                "staff",
                null,
                null,
                "Host assigned manually"
            );
        }

        Reservation reservation(ReservationStatus status) {
            return reservation(status, LocalDate.of(2026, 6, 20));
        }

        Reservation reservation(ReservationStatus status, LocalDate businessDate) {
            return new Reservation(
                reservationId,
                scope,
                customerId,
                new ReservationCode("R-SEAT-1"),
                new PartySize(4),
                new BusinessDate(businessDate),
                reservedStartAt,
                reservedEndAt,
                reservedStartAt.plusSeconds(15 * 60L),
                status,
                "staff",
                null,
                null,
                "Window seat preferred",
                Instant.parse("2026-06-18T01:00:00Z"),
                Instant.parse("2026-06-20T04:45:00Z"),
                null
            );
        }

        void replaceReservationBusinessDate(LocalDate businessDate) {
            reservationRepository.reservations.put(reservationId.value(), reservation(ReservationStatus.ARRIVED, businessDate));
        }

        DiningTable table(String code, CapacityRange capacity, DiningTableStatus status) {
            return new DiningTable(new TableId(UUID.randomUUID()), scope, areaId, code, capacity, status, true);
        }

        void replaceTableStatus(DiningTableStatus status) {
            DiningTable replacement = new DiningTable(table.id(), scope, areaId, table.tableCode(), table.capacity(), status, table.combinable());
            diningTableRepository.tables.put(table.id().value(), replacement);
        }

        void replaceTableCapacity(CapacityRange capacity) {
            DiningTable replacement = new DiningTable(table.id(), scope, areaId, table.tableCode(), capacity, table.status(), table.combinable());
            diningTableRepository.tables.put(table.id().value(), replacement);
        }

        void replaceGroupStatus(TableGroupStatus status) {
            tableGroupRepository.groups.put(
                group.id().value(),
                new TableGroup(group.id(), scope, group.groupCode(), group.groupType(), group.capacity(), status)
            );
        }

        void replaceGroupCapacity(CapacityRange capacity) {
            tableGroupRepository.groups.put(
                group.id().value(),
                new TableGroup(group.id(), scope, group.groupCode(), group.groupType(), capacity, group.status())
            );
        }

        void replaceGroupMemberStatus(DiningTableStatus status) {
            DiningTable replacement = new DiningTable(
                groupMemberTable.id(),
                scope,
                areaId,
                groupMemberTable.tableCode(),
                groupMemberTable.capacity(),
                status,
                groupMemberTable.combinable()
            );
            diningTableRepository.tables.put(groupMemberTable.id().value(), replacement);
        }

        Seating persistExistingReservationSeating(UUID resourceId, String resourceType) {
            Seating seating = new Seating(
                new SeatingId(UUID.randomUUID()),
                scope,
                "reservation",
                reservationId.value(),
                "S-existing",
                null,
                null,
                new PartySize(4),
                SeatingStatus.OCCUPIED
            );
            seatingRepository.persistedSeatings.put(seating.id().value(), seating);
            seatingRepository.persistedResources.add(new SeatingResource(
                UUID.randomUUID(),
                scope,
                seating.id(),
                resourceType,
                resourceId,
                "active"
            ));
            return seating;
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
            return Optional.empty();
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

    private static final class FakeReservationRepository implements ReservationRepositoryPort {
        final Map<UUID, Reservation> reservations = new HashMap<>();
        final List<Reservation> saved = new ArrayList<>();
        boolean failOnSave;

        @Override
        public Optional<Reservation> findById(StoreScope scope, ReservationId reservationId) {
            return Optional.ofNullable(reservations.get(reservationId.value()))
                .filter(reservation -> reservation.scope().equals(scope));
        }

        @Override
        public Optional<Reservation> findByCode(StoreScope scope, ReservationCode reservationCode) {
            return reservations.values().stream()
                .filter(reservation -> reservation.scope().equals(scope) && reservation.reservationCode().equals(reservationCode))
                .findFirst();
        }

        @Override
        public boolean existsByReservationCode(StoreScope scope, ReservationCode reservationCode) {
            return false;
        }

        @Override
        public List<Reservation> findStoreSchedule(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
            return List.of();
        }

        @Override
        public boolean existsActiveDuplicate(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
            return false;
        }

        @Override
        public List<Reservation> findActiveConflicts(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
            return List.of();
        }

        @Override
        public int findActiveCapacityUsage(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
            return 0;
        }

        @Override
        public Reservation save(StoreScope scope, Reservation reservation) {
            if (failOnSave) {
                throw new IllegalStateException("reservation save failed");
            }
            saved.add(reservation);
            reservations.put(reservation.id().value(), reservation);
            return reservation;
        }
    }

    private static final class FakeDiningTableRepository implements DiningTableRepositoryPort {
        final Map<UUID, DiningTable> tables = new HashMap<>();
        final List<DiningTable> saved = new ArrayList<>();

        @Override
        public Optional<DiningTable> findById(StoreScope scope, TableId tableId) {
            return Optional.ofNullable(tables.get(tableId.value())).filter(table -> table.scope().equals(scope));
        }

        @Override
        public List<DiningTable> findActiveByArea(StoreScope scope, UUID areaId) {
            return tables.values().stream().filter(table -> table.scope().equals(scope) && table.areaId().equals(areaId)).toList();
        }

        @Override
        public List<DiningTable> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return tables.values().stream().filter(table -> table.scope().equals(scope)).toList();
        }

        @Override
        public DiningTable save(StoreScope scope, DiningTable table) {
            saved.add(table);
            tables.put(table.id().value(), table);
            return table;
        }
    }

    private static final class FakeTableGroupRepository implements TableGroupRepositoryPort {
        final Map<UUID, TableGroup> groups = new HashMap<>();
        final List<TableGroupMember> members = new ArrayList<>();
        final List<TableGroup> saved = new ArrayList<>();

        @Override
        public Optional<TableGroup> findById(StoreScope scope, TableGroupId tableGroupId) {
            return Optional.ofNullable(groups.get(tableGroupId.value())).filter(group -> group.scope().equals(scope));
        }

        @Override
        public List<TableGroupMember> findActiveMembers(StoreScope scope, TableGroupId tableGroupId) {
            return members.stream().filter(member -> member.scope().equals(scope) && member.tableGroupId().equals(tableGroupId)).toList();
        }

        @Override
        public List<TableGroup> findActiveGroupsForTable(StoreScope scope, TableId tableId) {
            return List.of();
        }

        @Override
        public List<TableGroup> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate) {
            return groups.values().stream().filter(group -> group.scope().equals(scope)).toList();
        }

        @Override
        public TableGroup save(StoreScope scope, TableGroup tableGroup) {
            saved.add(tableGroup);
            groups.put(tableGroup.id().value(), tableGroup);
            return tableGroup;
        }

        @Override
        public TableGroupMember saveMember(StoreScope scope, TableGroupMember member) {
            members.add(member);
            return member;
        }
    }

    private static final class FakeTableLockRepository implements TableLockRepositoryPort {
        boolean conflict;

        @Override
        public Optional<TableLock> findActiveByResource(StoreScope scope, String resourceType, UUID resourceId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveConflict(StoreScope scope, String resourceType, UUID resourceId, OffsetDateTime at) {
            return conflict;
        }

        @Override
        public TableLock save(StoreScope scope, TableLock lock) {
            return lock;
        }

        @Override
        public TableLock release(StoreScope scope, UUID tableLockId, OffsetDateTime releasedAt) {
            throw new UnsupportedOperationException("not used by reservation arrived direct seating");
        }
    }

    private static final class FakeSeatingRepository implements SeatingRepositoryPort {
        final Map<UUID, Seating> persistedSeatings = new HashMap<>();
        final List<SeatingResource> persistedResources = new ArrayList<>();
        final List<Seating> saved = new ArrayList<>();
        final List<SeatingResource> savedResources = new ArrayList<>();
        boolean failOnSave;

        @Override
        public Optional<Seating> findById(StoreScope scope, SeatingId seatingId) {
            return Optional.ofNullable(persistedSeatings.get(seatingId.value())).filter(seating -> seating.scope().equals(scope));
        }

        @Override
        public Optional<Seating> findActiveBySource(StoreScope scope, String sourceType, UUID sourceId) {
            return persistedSeatings.values().stream()
                .filter(seating -> seating.scope().equals(scope))
                .filter(seating -> sourceType.equals(seating.sourceType()) && sourceId.equals(seating.sourceId()))
                .filter(seating -> seating.status() == SeatingStatus.OCCUPIED || seating.status() == SeatingStatus.LOCKED || seating.status() == SeatingStatus.PLANNED)
                .findFirst();
        }

        @Override
        public boolean existsActiveResourceOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return persistedResources.stream()
                .anyMatch(resource -> resource.scope().equals(scope)
                    && resource.status().equals("active")
                    && resource.resourceType().equals(resourceType)
                    && resource.resourceId().equals(resourceId));
        }

        @Override
        public Optional<Seating> findActiveOccupancy(StoreScope scope, String resourceType, UUID resourceId) {
            return persistedResources.stream()
                .filter(resource -> resource.scope().equals(scope)
                    && resource.status().equals("active")
                    && resource.resourceType().equals(resourceType)
                    && resource.resourceId().equals(resourceId))
                .findFirst()
                .flatMap(resource -> findById(scope, resource.seatingId()));
        }

        @Override
        public Optional<SeatingResource> findActiveResourceBySeating(StoreScope scope, SeatingId seatingId) {
            return persistedResources.stream()
                .filter(resource -> resource.scope().equals(scope) && resource.seatingId().equals(seatingId) && resource.status().equals("active"))
                .findFirst();
        }

        @Override
        public Seating save(StoreScope scope, Seating seating) {
            if (failOnSave) {
                throw new IllegalStateException("seating save failed");
            }
            saved.add(seating);
            persistedSeatings.put(seating.id().value(), seating);
            return seating;
        }

        @Override
        public SeatingResource saveResource(StoreScope scope, SeatingResource resource) {
            if (failOnSave) {
                throw new IllegalStateException("resource save failed");
            }
            savedResources.add(resource);
            persistedResources.add(resource);
            return resource;
        }
    }

    private static final class FakeBusinessEventRepository implements BusinessEventRepositoryPort {
        final List<BusinessEvent> events = new ArrayList<>();
        boolean failOnAppend;

        @Override
        public BusinessEvent append(StoreScope scope, BusinessEvent event) {
            if (failOnAppend) {
                throw new IllegalStateException("event append failed");
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
                throw new IllegalStateException("transition append failed");
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
            return logs.stream()
                .filter(log -> log.targetType().equals(targetType) && log.targetId().equals(targetId))
                .reduce((first, second) -> second);
        }
    }

    private static final class FakeAuditLogRepository implements AuditLogRepositoryPort {
        final List<AuditLog> logs = new ArrayList<>();
        boolean failOnAppend;

        @Override
        public AuditLog append(StoreScope scope, AuditLog auditLog) {
            if (failOnAppend) {
                throw new IllegalStateException("audit append failed");
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
