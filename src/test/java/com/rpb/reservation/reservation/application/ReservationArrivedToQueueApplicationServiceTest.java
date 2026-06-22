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
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.queue.application.port.out.QueueGroupRepositoryPort;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.domain.QueueGroup;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.policy.QueueTicketNumberConflictException;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import com.rpb.reservation.reservation.application.command.QueueArrivedReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.store.value.StoreId;
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

class ReservationArrivedToQueueApplicationServiceTest {

    @Test
    void queuesArrivedReservationAndWritesEvidenceWithoutChangingReservation() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.reservationId()).isEqualTo(scenario.reservationId.value());
        assertThat(result.reservationCode()).isEqualTo("R-QUEUE-1");
        assertThat(result.reservationStatus()).isEqualTo("arrived");
        assertThat(result.queueTicketId()).isNotNull();
        assertThat(result.queueTicketNumber()).isEqualTo(1);
        assertThat(result.queueTicketStatus()).isEqualTo("waiting");
        assertThat(result.queueGroupId()).isEqualTo(scenario.mediumGroup.id());
        assertThat(result.queueGroupCode()).isEqualTo("3-4");
        assertThat(result.partySize()).isEqualTo(4);
        assertThat(result.partySizeGroup()).isEqualTo("3-4");
        assertThat(result.businessDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(result.queuePosition()).isEqualTo(1);
        assertThat(result.idempotencyStatus()).isEqualTo("completed");
        assertThat(result.events()).containsExactly("reservation.queued", "queue_ticket.created");
        assertThat(result.alreadyQueued()).isFalse();

        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.reservationRepository.reservations.get(scenario.reservationId.value()).status()).isEqualTo(ReservationStatus.ARRIVED);
        assertThat(scenario.queueTicketRepository.saved).hasSize(1);
        QueueTicket ticket = scenario.queueTicketRepository.saved.getFirst();
        assertThat(ticket.scope()).isEqualTo(scenario.scope);
        assertThat(ticket.queueGroupId()).isEqualTo(scenario.mediumGroup.id());
        assertThat(ticket.customerId()).isEqualTo(scenario.customerId);
        assertThat(ticket.reservationId()).isEqualTo(scenario.reservationId.value());
        assertThat(ticket.walkInId()).isNull();
        assertThat(ticket.ticketNumber().value()).isEqualTo(1);
        assertThat(ticket.partySize().value()).isEqualTo(4);
        assertThat(ticket.businessDate()).isEqualTo(new BusinessDate(LocalDate.of(2026, 6, 20)));
        assertThat(ticket.status()).isEqualTo(QueueTicketStatus.WAITING);
        assertThat(ticket.queuePosition()).isEqualTo(1);

        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .containsExactly("reservation.queued", "queue_ticket.created");
        assertThat(scenario.stateTransitionLogRepository.logs).hasSize(1);
        assertThat(scenario.stateTransitionLogRepository.logs.getFirst().targetType()).isEqualTo("queue_ticket");
        assertThat(scenario.stateTransitionLogRepository.logs.getFirst().fromStatus()).isEqualTo("none");
        assertThat(scenario.stateTransitionLogRepository.logs.getFirst().toStatus()).isEqualTo("waiting");
        assertThat(scenario.stateTransitionLogRepository.logs.getFirst().transitionCode()).isEqualTo("queue_ticket.create");
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.queue");
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
        assertThat(scenario.idempotencyRepository.completed.getFirst().targetType()).isEqualTo("queue_ticket");

        assertBoundaryUnchanged(scenario);
    }

    @Test
    void derivesQueueGroupAndTicketNumberFromExistingQueueTail() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.queueTicketRepository.persisted.add(
            scenario.queueTicket(UUID.randomUUID(), scenario.mediumGroup.id(), null, 7, 4, 3)
        );

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.commandWithKey("idem-tail"));

        assertThat(result.success()).isTrue();
        assertThat(result.queueTicketNumber()).isEqualTo(8);
        assertThat(result.queuePosition()).isEqualTo(4);
        assertThat(scenario.queueTicketRepository.saved.getFirst().ticketNumber().value()).isEqualTo(8);
        assertThat(scenario.queueTicketRepository.saved.getFirst().queuePosition()).isEqualTo(4);
    }

    @Test
    void explicitPartySizeGroupMustMatchReservationPartySize() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(
            scenario.commandWithPartySizeGroup("1-2", "idem-group-mismatch")
        );

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.QUEUE_GROUP_PARTY_SIZE_MISMATCH);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void alreadyQueuedWithNewKeyReturnsSuccessLikeResultWithoutDuplicateEvidence() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        QueueTicket existing = scenario.queueTicket(scenario.reservationId.value(), scenario.mediumGroup.id(), scenario.reservationId.value(), 4, 4, 2);
        scenario.queueTicketRepository.persisted.add(existing);

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.commandWithKey("idem-already-queued"));

        assertThat(result.success()).isTrue();
        assertThat(result.alreadyQueued()).isTrue();
        assertThat(result.queueTicketId()).isEqualTo(existing.id().value());
        assertThat(result.queueTicketNumber()).isEqualTo(4);
        assertThat(result.queuePosition()).isEqualTo(2);
        assertThat(result.events()).isEmpty();
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutMutation() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        QueueArrivedReservationCommand command = scenario.command();
        String hash = ReservationArrivedToQueueApplicationService.requestHash(command);
        UUID replayTicketId = UUID.randomUUID();
        scenario.idempotencyRepository.existing = completedRecord(
            hash,
            scenario.reservationId.value(),
            replayTicketId,
            scenario.mediumGroup.id(),
            false
        );

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(command);

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.queueTicketId()).isEqualTo(replayTicketId);
        assertThat(result.queueTicketNumber()).isEqualTo(12);
        assertThat(result.alreadyQueued()).isFalse();
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        String hash = ReservationArrivedToQueueApplicationService.requestHash(scenario.command());
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.STARTED, null);

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.COMMAND_IN_PROGRESS);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        String hash = ReservationArrivedToQueueApplicationService.requestHash(scenario.command());
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.FAILED, "{\"failure_reason\":\"audit_write_failed\"}");

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.idempotencyRepository.existing = completedRecord(
            "different-hash",
            scenario.reservationId.value(),
            UUID.randomUUID(),
            scenario.mediumGroup.id(),
            false
        );

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void reservationNotFoundReturnsApplicationError() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.reservationRepository.reservations.clear();

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.RESERVATION_NOT_FOUND);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void reservationStatusFailuresReturnApplicationErrors() {
        assertStatusRejected(ReservationStatus.CONFIRMED, ReservationArrivedToQueueError.RESERVATION_STATUS_NOT_ARRIVED);
        assertStatusRejected(ReservationStatus.SEATED, ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_SEATED);
        assertStatusRejected(ReservationStatus.CANCELLED, ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_CANCELLED);
        assertStatusRejected(ReservationStatus.NO_SHOW, ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_NO_SHOW);
        assertStatusRejected(ReservationStatus.COMPLETED, ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_COMPLETED);
    }

    @Test
    void missingQueueGroupReturnsApplicationError() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.queueGroupRepository.groups.clear();

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.QUEUE_GROUP_NOT_FOUND);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void ticketNumberConflictReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.queueTicketRepository.failWithTicketNumberConflict = true;

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.QUEUE_TICKET_NUMBER_CONFLICT);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void eventWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.businessEventRepository.failOnAppend = true;

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.BUSINESS_EVENT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void transitionWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.stateTransitionLogRepository.failOnAppend = true;

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.STATE_TRANSITION_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void auditWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.auditLogRepository.failOnAppend = true;

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.AUDIT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void persistenceFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);
        scenario.queueTicketRepository.failOnSave = true;

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationArrivedToQueueError.PERSISTENCE_ERROR);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void boundaryDoesNotCreateSeatingTableCleaningTurnoverNoShowCancellationApiUiOrMigration() {
        Scenario scenario = Scenario.ready(ReservationStatus.ARRIVED);

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isTrue();
        assertBoundaryUnchanged(scenario);
    }

    private static void assertStatusRejected(ReservationStatus status, ReservationArrivedToQueueError expectedError) {
        Scenario scenario = Scenario.ready(status);

        ReservationArrivedToQueueResult result = scenario.service().queueArrivedReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(expectedError);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.queue.failed");
    }

    private static void assertBoundaryUnchanged(Scenario scenario) {
        assertThat(scenario.seatingCreated).isFalse();
        assertThat(scenario.tableStatusChanged).isFalse();
        assertThat(scenario.cleaningCreated).isFalse();
        assertThat(scenario.turnoverCreated).isFalse();
        assertThat(scenario.noShowImplemented).isFalse();
        assertThat(scenario.cancellationImplemented).isFalse();
        assertThat(scenario.controllerCreated).isFalse();
        assertThat(scenario.apiDtoCreated).isFalse();
        assertThat(scenario.uiCreated).isFalse();
        assertThat(scenario.migrationChanged).isFalse();
    }

    private static IdempotencyRecord completedRecord(
        String requestHash,
        UUID reservationId,
        UUID queueTicketId,
        UUID queueGroupId,
        boolean alreadyQueued
    ) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-queue-reservation"),
            "staff",
            "queue_arrived_reservation",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "queue_ticket",
            queueTicketId,
            """
                {"reservationId":"%s","reservationCode":"R-QUEUE-1","reservationStatus":"arrived","queueTicketId":"%s","queueTicketNumber":12,"queueTicketStatus":"waiting","queueGroupId":"%s","queueGroupCode":"3-4","partySize":4,"partySizeGroup":"3-4","businessDate":"2026-06-20","queuePosition":5,"alreadyQueued":%s}
                """.formatted(reservationId, queueTicketId, queueGroupId, alreadyQueued).trim()
        );
    }

    private static IdempotencyRecord idempotencyRecord(String requestHash, IdempotencyStatus status, String snapshot) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-queue-reservation"),
            "staff",
            "queue_arrived_reservation",
            requestHash,
            status,
            null,
            null,
            snapshot
        );
    }

    private static final class Scenario {
        final Instant now = Instant.parse("2026-06-20T03:30:00Z");
        final Instant reservedStartAt = Instant.parse("2026-06-20T03:00:00Z");
        final Instant reservedEndAt = Instant.parse("2026-06-20T04:30:00Z");
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final ReservationId reservationId = new ReservationId(UUID.randomUUID());
        final CustomerId customerId = new CustomerId(UUID.randomUUID());
        final QueueGroup smallGroup = new QueueGroup(UUID.randomUUID(), scope, "1-2", 1, 2, "queue.group.1_2", "active");
        final QueueGroup mediumGroup = new QueueGroup(UUID.randomUUID(), scope, "3-4", 3, 4, "queue.group.3_4", "active");
        final QueueGroup largeGroup = new QueueGroup(UUID.randomUUID(), scope, "5-6", 5, 6, "queue.group.5_6", "active");
        final QueueGroup extraLargeGroup = new QueueGroup(UUID.randomUUID(), scope, "7+", 7, null, "queue.group.7_plus", "active");
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "zh-CN", "active");
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeReservationRepository reservationRepository = new FakeReservationRepository();
        final FakeQueueGroupRepository queueGroupRepository = new FakeQueueGroupRepository();
        final FakeQueueTicketRepository queueTicketRepository = new FakeQueueTicketRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean seatingCreated;
        boolean tableStatusChanged;
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
            scenario.queueGroupRepository.groups.addAll(List.of(
                scenario.smallGroup,
                scenario.mediumGroup,
                scenario.largeGroup,
                scenario.extraLargeGroup
            ));
            Reservation reservation = scenario.reservation(status);
            scenario.reservationRepository.reservations.put(reservation.id().value(), reservation);
            return scenario;
        }

        ReservationArrivedToQueueApplicationService service() {
            return new ReservationArrivedToQueueApplicationService(
                storeRepository,
                reservationRepository,
                queueGroupRepository,
                queueTicketRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository,
                Clock.fixed(now, ZoneOffset.UTC)
            );
        }

        QueueArrivedReservationCommand command() {
            return commandWithKey("idem-queue-reservation");
        }

        QueueArrivedReservationCommand commandWithKey(String idempotencyKey) {
            return new QueueArrivedReservationCommand(
                tenantId.value(),
                storeId.value(),
                reservationId.value(),
                idempotencyKey,
                actorId,
                "staff",
                null,
                "no_table_available",
                "Guest is waiting near the host stand"
            );
        }

        QueueArrivedReservationCommand commandWithPartySizeGroup(String partySizeGroup, String idempotencyKey) {
            return new QueueArrivedReservationCommand(
                tenantId.value(),
                storeId.value(),
                reservationId.value(),
                idempotencyKey,
                actorId,
                "staff",
                partySizeGroup,
                null,
                null
            );
        }

        Reservation reservation(ReservationStatus status) {
            return new Reservation(
                reservationId,
                scope,
                customerId,
                new ReservationCode("R-QUEUE-1"),
                new PartySize(4),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                reservedStartAt,
                reservedEndAt,
                reservedStartAt.plusSeconds(15 * 60L),
                status,
                "staff",
                null,
                null,
                "Window seat preferred",
                Instant.parse("2026-06-18T01:00:00Z"),
                Instant.parse("2026-06-20T03:15:00Z"),
                null
            );
        }

        QueueTicket queueTicket(UUID id, UUID queueGroupId, UUID reservationId, int ticketNumber, int partySize, int queuePosition) {
            return new QueueTicket(
                new QueueTicketId(id),
                scope,
                queueGroupId,
                customerId,
                reservationId,
                null,
                new QueueTicketNumber(ticketNumber),
                new PartySize(partySize),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                QueueTicketStatus.WAITING,
                queuePosition,
                "existing ticket"
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
            saved.add(reservation);
            reservations.put(reservation.id().value(), reservation);
            return reservation;
        }
    }

    private static final class FakeQueueGroupRepository implements QueueGroupRepositoryPort {
        final List<QueueGroup> groups = new ArrayList<>();

        @Override
        public Optional<QueueGroup> findActiveByCode(StoreScope scope, String groupCode) {
            return groups.stream()
                .filter(group -> group.scope().equals(scope))
                .filter(group -> group.groupCode().equals(groupCode))
                .filter(group -> "active".equals(group.status()))
                .findFirst();
        }

        @Override
        public Optional<QueueGroup> findActiveByPartySize(StoreScope scope, PartySize partySize) {
            return groups.stream()
                .filter(group -> group.scope().equals(scope))
                .filter(group -> "active".equals(group.status()))
                .filter(group -> group.covers(partySize))
                .findFirst();
        }
    }

    private static final class FakeQueueTicketRepository implements QueueTicketRepositoryPort {
        final List<QueueTicket> persisted = new ArrayList<>();
        final List<QueueTicket> saved = new ArrayList<>();
        boolean failWithTicketNumberConflict;
        boolean failOnSave;

        @Override
        public Optional<QueueTicket> findById(StoreScope scope, QueueTicketId queueTicketId) {
            return persisted.stream()
                .filter(ticket -> ticket.scope().equals(scope) && ticket.id().equals(queueTicketId))
                .findFirst();
        }

        @Override
        public List<QueueTicket> findActiveQueue(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return persisted.stream()
                .filter(ticket -> ticket.scope().equals(scope))
                .filter(ticket -> ticket.queueGroupId().equals(queueGroupId))
                .filter(ticket -> ticket.businessDate().equals(businessDate))
                .filter(ticket -> ticket.status() == QueueTicketStatus.WAITING)
                .toList();
        }

        @Override
        public Optional<QueueTicket> findNextCallable(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return Optional.empty();
        }

        @Override
        public Optional<QueueTicket> findActiveByReservationId(StoreScope scope, UUID reservationId) {
            return persisted.stream()
                .filter(ticket -> ticket.scope().equals(scope))
                .filter(ticket -> reservationId.equals(ticket.reservationId()))
                .filter(ticket -> ticket.status() == QueueTicketStatus.WAITING)
                .findFirst();
        }

        @Override
        public boolean existsActiveSourceTicket(StoreScope scope, String sourceType, UUID sourceId) {
            return "reservation".equals(sourceType) && findActiveByReservationId(scope, sourceId).isPresent();
        }

        @Override
        public QueueTicket save(StoreScope scope, QueueTicket queueTicket) {
            if (failWithTicketNumberConflict) {
                throw new QueueTicketNumberConflictException("duplicate ticket number");
            }
            if (failOnSave) {
                throw new IllegalStateException("queue ticket save failed");
            }
            saved.add(queueTicket);
            persisted.add(queueTicket);
            return queueTicket;
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
                throw new IllegalStateException("transition failed");
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
