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
import com.rpb.reservation.reservation.application.command.CancelReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.service.ReservationCancelApplicationService;
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

class ReservationCancelApplicationServiceTest {

    @Test
    void cancelsConfirmedReservationAndWritesCancellationEvidence() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.reservationId()).isEqualTo(scenario.reservationId.value());
        assertThat(result.reservationCode()).isEqualTo("R-CANCEL-1");
        assertThat(result.status()).isEqualTo("cancelled");
        assertThat(result.cancelledAt()).isEqualTo(scenario.cancelledAt);
        assertThat(result.cancellationReasonCode()).isEqualTo("guest_requested");
        assertThat(result.alreadyCancelled()).isFalse();
        assertThat(result.idempotencyStatus()).isEqualTo("completed");
        assertThat(result.events()).containsExactly("reservation.cancelled");

        assertThat(scenario.reservationRepository.saved).hasSize(1);
        Reservation saved = scenario.reservationRepository.saved.getFirst();
        assertThat(saved.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(saved.cancellationReasonCode()).isEqualTo("guest_requested");
        assertThat(saved.updatedAt()).isEqualTo(scenario.cancelledAt);
        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType).containsExactly("reservation.cancelled");
        assertThat(scenario.businessEventRepository.events.getFirst().metadata()).contains("\"cancelledAt\":\"" + scenario.cancelledAt + "\"");
        assertThat(scenario.businessEventRepository.events.getFirst().metadata()).contains("\"reasonCode\":\"guest_requested\"");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::fromStatus).containsExactly("confirmed");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::toStatus).containsExactly("cancelled");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::transitionCode).containsExactly("reservation.cancel");
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.cancel");
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
        assertThat(scenario.idempotencyRepository.completed.getFirst().targetType()).isEqualTo("reservation");
    }

    @Test
    void cancelsDraftReservation() {
        Scenario scenario = Scenario.ready(ReservationStatus.DRAFT);

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("cancelled");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::fromStatus).containsExactly("draft");
        assertThat(scenario.stateTransitionLogRepository.logs).extracting(StateTransitionLog::toStatus).containsExactly("cancelled");
    }

    @Test
    void usesApplicationClockWhenCancelledAtIsMissing() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.commandWithoutCancelledAt());

        assertThat(result.success()).isTrue();
        assertThat(result.cancelledAt()).isEqualTo(scenario.now);
        assertThat(scenario.reservationRepository.saved.getFirst().updatedAt()).isEqualTo(scenario.now);
    }

    @Test
    void alreadyCancelledWithNewKeyReturnsSuccessLikeResultWithoutDuplicateEvidence() {
        Scenario scenario = Scenario.ready(ReservationStatus.CANCELLED);
        scenario.stateTransitionLogRepository.logs.add(scenario.cancellationTransition(scenario.previousCancelledAt));

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("cancelled");
        assertThat(result.alreadyCancelled()).isTrue();
        assertThat(result.cancelledAt()).isEqualTo(scenario.previousCancelledAt);
        assertThat(result.events()).isEmpty();
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).hasSize(1);
        assertThat(scenario.auditLogRepository.logs).isEmpty();
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutMutation() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        String hash = ReservationCancelApplicationService.requestHash(scenario.command());
        scenario.idempotencyRepository.existing = completedRecord(
            hash,
            scenario.reservationId.value(),
            "R-CANCEL-1",
            scenario.cancelledAt,
            "guest_requested",
            false
        );

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.status()).isEqualTo("cancelled");
        assertThat(result.cancelledAt()).isEqualTo(scenario.cancelledAt);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        String hash = ReservationCancelApplicationService.requestHash(scenario.command());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-cancel"),
            "staff",
            "cancel_reservation",
            hash,
            IdempotencyStatus.STARTED,
            null,
            null,
            null
        );

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(ReservationCancelError.COMMAND_IN_PROGRESS);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        String hash = ReservationCancelApplicationService.requestHash(scenario.command());
        scenario.idempotencyRepository.existing = new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-cancel"),
            "staff",
            "cancel_reservation",
            hash,
            IdempotencyStatus.FAILED,
            null,
            null,
            "{\"failure_reason\":\"audit_write_failed\"}"
        );

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        scenario.idempotencyRepository.existing = completedRecord(
            "different-hash",
            scenario.reservationId.value(),
            "R-CANCEL-1",
            scenario.cancelledAt,
            "guest_requested",
            false
        );

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void missingIdempotencyKeyIsRejectedBeforeStartingIdempotency() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.commandWithIdempotencyKey(null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.MISSING_IDEMPOTENCY_KEY);
        assertThat(scenario.idempotencyRepository.started).isEmpty();
    }

    @Test
    void reservationNotFoundReturnsApplicationError() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        scenario.reservationRepository.reservations.clear();

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.RESERVATION_NOT_FOUND);
        assertThat(scenario.reservationRepository.saved).isEmpty();
    }

    @Test
    void arrivedReservationCannotCancelInSafetyV1() {
        assertStatusRejected(ReservationStatus.ARRIVED, ReservationCancelError.RESERVATION_CANNOT_CANCEL_ARRIVED);
    }

    @Test
    void seatedReservationCannotCancelInSafetyV1() {
        assertStatusRejected(ReservationStatus.SEATED, ReservationCancelError.RESERVATION_CANNOT_CANCEL_SEATED);
    }

    @Test
    void noShowReservationCannotCancelInSafetyV1() {
        assertStatusRejected(ReservationStatus.NO_SHOW, ReservationCancelError.RESERVATION_CANNOT_CANCEL_NO_SHOW);
    }

    @Test
    void completedReservationCannotCancelInSafetyV1() {
        assertStatusRejected(ReservationStatus.COMPLETED, ReservationCancelError.RESERVATION_CANNOT_CANCEL_COMPLETED);
    }

    @Test
    void persistenceFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        scenario.reservationRepository.failOnSave = true;

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.REPOSITORY_SAVE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void eventWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        scenario.businessEventRepository.failOnAppend = true;

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.BUSINESS_EVENT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void transitionWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        scenario.stateTransitionLogRepository.failOnAppend = true;

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.STATE_TRANSITION_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void auditWriteFailureReturnsApplicationErrorAndMarksIdempotencyFailed() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);
        scenario.auditLogRepository.failOnAppend = true;

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(ReservationCancelError.AUDIT_WRITE_FAILED);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    @Test
    void boundaryDoesNotCancelQueueTicketsSeatingsTablesNoShowOrMigration() {
        Scenario scenario = Scenario.ready(ReservationStatus.CONFIRMED);

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(scenario.queueTicketCancelled).isFalse();
        assertThat(scenario.seatingCancelled).isFalse();
        assertThat(scenario.tableLockReleased).isFalse();
        assertThat(scenario.tableStatusChanged).isFalse();
        assertThat(scenario.noShowImplemented).isFalse();
        assertThat(scenario.migrationChanged).isFalse();
    }

    private static void assertStatusRejected(ReservationStatus status, ReservationCancelError expectedError) {
        Scenario scenario = Scenario.ready(status);

        ReservationCancelResult result = scenario.service().cancelReservation(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(expectedError);
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("reservation.cancel.failed");
    }

    private static IdempotencyRecord completedRecord(
        String requestHash,
        UUID reservationId,
        String reservationCode,
        Instant cancelledAt,
        String reasonCode,
        boolean alreadyCancelled
    ) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-cancel"),
            "staff",
            "cancel_reservation",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "reservation",
            reservationId,
            """
                {"reservationId":"%s","reservationCode":"%s","status":"cancelled","cancelledAt":"%s","cancellationReasonCode":%s,"alreadyCancelled":%s}
                """.formatted(reservationId, reservationCode, cancelledAt, jsonNullable(reasonCode), alreadyCancelled).trim()
        );
    }

    private static final class Scenario {
        final Instant now = Instant.parse("2026-06-20T02:00:00Z");
        final Instant cancelledAt = Instant.parse("2026-06-20T03:20:00Z");
        final Instant previousCancelledAt = Instant.parse("2026-06-20T02:58:00Z");
        final Instant reservedStartAt = Instant.parse("2026-06-20T03:00:00Z");
        final Instant reservedEndAt = Instant.parse("2026-06-20T04:30:00Z");
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final ReservationId reservationId = new ReservationId(UUID.randomUUID());
        final CustomerId customerId = new CustomerId(UUID.randomUUID());
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "en-SG", "active");
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeReservationRepository reservationRepository = new FakeReservationRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean queueTicketCancelled;
        boolean seatingCancelled;
        boolean tableLockReleased;
        boolean tableStatusChanged;
        boolean noShowImplemented;
        boolean migrationChanged;

        static Scenario ready(ReservationStatus status) {
            Scenario scenario = new Scenario();
            Reservation reservation = scenario.reservation(status);
            scenario.reservationRepository.reservations.put(reservation.id().value(), reservation);
            return scenario;
        }

        ReservationCancelApplicationService service() {
            return new ReservationCancelApplicationService(
                storeRepository,
                reservationRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository,
                Clock.fixed(now, ZoneOffset.UTC)
            );
        }

        CancelReservationCommand command() {
            return new CancelReservationCommand(
                tenantId.value(),
                storeId.value(),
                reservationId.value(),
                "idem-cancel",
                actorId,
                "staff",
                cancelledAt,
                "guest_requested",
                "Customer called to cancel"
            );
        }

        CancelReservationCommand commandWithoutCancelledAt() {
            return new CancelReservationCommand(
                tenantId.value(),
                storeId.value(),
                reservationId.value(),
                "idem-cancel-clock",
                actorId,
                "staff",
                null,
                null,
                null
            );
        }

        CancelReservationCommand commandWithIdempotencyKey(String idempotencyKey) {
            return new CancelReservationCommand(
                tenantId.value(),
                storeId.value(),
                reservationId.value(),
                idempotencyKey,
                actorId,
                "staff",
                cancelledAt,
                null,
                null
            );
        }

        Reservation reservation(ReservationStatus status) {
            return new Reservation(
                reservationId,
                scope,
                customerId,
                new ReservationCode("R-CANCEL-1"),
                new PartySize(4),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                reservedStartAt,
                reservedEndAt,
                reservedStartAt.plusSeconds(15 * 60L),
                status,
                "staff",
                status == ReservationStatus.CANCELLED ? "guest_requested" : null,
                null,
                "Window seat preferred",
                Instant.parse("2026-06-18T01:00:00Z"),
                Instant.parse("2026-06-18T01:00:00Z"),
                null
            );
        }

        StateTransitionLog cancellationTransition(Instant occurredAt) {
            return new StateTransitionLog(
                UUID.randomUUID(),
                "reservation",
                reservationId.value(),
                "confirmed",
                "cancelled",
                "reservation.cancel",
                "staff",
                actorId,
                "staff",
                "{\"cancelledAt\":\"" + occurredAt + "\"}"
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

    private static String jsonNullable(String value) {
        return value == null || value.isBlank() ? "null" : "\"" + value.trim() + "\"";
    }
}
