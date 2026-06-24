package com.rpb.reservation.queue.application;

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
import com.rpb.reservation.queue.application.command.SkipQueueTicketCommand;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.service.QueueSkipApplicationService;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.persistence.entity.QueueTicketEntity;
import com.rpb.reservation.queue.persistence.mapper.DefaultQueueTicketMapper;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
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

class QueueSkipApplicationServiceTest {

    @Test
    void skipsCalledQueueTicketAndWritesEvidenceWithoutChangingReservation() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.command());

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.queueTicketId()).isEqualTo(scenario.queueTicketId.value());
        assertThat(result.queueTicketNumber()).isEqualTo(27);
        assertThat(result.queueTicketStatus()).isEqualTo("skipped");
        assertThat(result.reservationId()).isEqualTo(scenario.reservationId.value());
        assertThat(result.reservationCode()).isEqualTo("R-SKIP-1");
        assertThat(result.reservationStatus()).isEqualTo("arrived");
        assertThat(result.skippedAt()).isEqualTo(scenario.now);
        assertThat(result.alreadySkipped()).isFalse();
        assertThat(result.events()).containsExactly("queue_ticket.skipped");
        assertThat(result.idempotencyStatus()).isEqualTo("completed");

        assertThat(scenario.queueTicketRepository.saved).hasSize(1);
        QueueTicket saved = scenario.queueTicketRepository.saved.getFirst();
        assertThat(saved.status()).isEqualTo(QueueTicketStatus.SKIPPED);
        assertThat(saved.skippedAt()).isEqualTo(scenario.now);

        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.reservationRepository.reservations.get(scenario.reservationId.value()).status())
            .isEqualTo(ReservationStatus.ARRIVED);

        assertThat(scenario.businessEventRepository.events).extracting(BusinessEvent::eventType)
            .containsExactly("queue_ticket.skipped");
        assertThat(scenario.stateTransitionLogRepository.logs).hasSize(1);
        StateTransitionLog transition = scenario.stateTransitionLogRepository.logs.getFirst();
        assertThat(transition.targetType()).isEqualTo("queue_ticket");
        assertThat(transition.fromStatus()).isEqualTo("called");
        assertThat(transition.toStatus()).isEqualTo("skipped");
        assertThat(transition.transitionCode()).isEqualTo("queue_ticket.skip");
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("queue.skip");
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
        assertThat(scenario.idempotencyRepository.completed.getFirst().targetType()).isEqualTo("queue_ticket");

        assertBoundaryUnchanged(scenario);
    }

    @Test
    void usesCommandSkippedAtWhenProvided() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        Instant provided = Instant.parse("2026-06-20T03:45:00Z");

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.commandWithSkippedAt(provided, "idem-skip-provided"));

        assertThat(result.success()).isTrue();
        assertThat(result.skippedAt()).isEqualTo(provided);
        assertThat(scenario.queueTicketRepository.saved.getFirst().skippedAt()).isEqualTo(provided);
    }

    @Test
    void alreadySkippedWithCompleteEvidenceReturnsSuccessLikeResultWithoutDuplicateWrites() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.SKIPPED, ReservationStatus.ARRIVED);
        Instant existingSkippedAt = Instant.parse("2026-06-20T03:40:00Z");
        scenario.queueTicketRepository.persisted.put(
            scenario.queueTicketId.value(),
            scenario.queueTicket(QueueTicketStatus.SKIPPED, scenario.calledAt, scenario.expiresAt, existingSkippedAt)
        );
        scenario.persistSkipEvidence();

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.commandWithKey("idem-already-skipped"));

        assertThat(result.success()).isTrue();
        assertThat(result.alreadySkipped()).isTrue();
        assertThat(result.skippedAt()).isEqualTo(existingSkippedAt);
        assertThat(result.events()).isEmpty();
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.reservationRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).hasSize(1);
        assertThat(scenario.stateTransitionLogRepository.logs).hasSize(1);
        assertThat(scenario.auditLogRepository.logs).hasSize(1);
        assertThat(scenario.idempotencyRepository.completed).hasSize(1);
    }

    @Test
    void alreadySkippedWithoutCompleteEvidenceReturnsApplicationError() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.SKIPPED, ReservationStatus.ARRIVED);
        scenario.queueTicketRepository.persisted.put(
            scenario.queueTicketId.value(),
            scenario.queueTicket(QueueTicketStatus.SKIPPED, scenario.calledAt, scenario.expiresAt, null)
        );

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.commandWithKey("idem-skipped-missing-evidence"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(QueueSkipError.QUEUE_SKIP_EVIDENCE_INCOMPLETE);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).extracting(AuditLog::operationCode).contains("queue.skip.failed");
    }

    @Test
    void completedSameHashReplaysStoredResultWithoutMutation() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        SkipQueueTicketCommand command = scenario.command();
        String hash = QueueSkipApplicationService.requestHash(command);
        scenario.idempotencyRepository.existing = completedRecord(hash, scenario);

        QueueSkipResult result = scenario.service().skipQueueTicket(command);

        assertThat(result.success()).isTrue();
        assertThat(result.replayed()).isTrue();
        assertThat(result.queueTicketStatus()).isEqualTo("skipped");
        assertThat(result.skippedAt()).isEqualTo(Instant.parse("2026-06-20T03:40:00Z"));
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
        assertThat(scenario.auditLogRepository.logs).isEmpty();
    }

    @Test
    void inProgressSameHashReturnsRetryLaterWithoutMutation() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        String hash = QueueSkipApplicationService.requestHash(scenario.command());
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.STARTED, null);

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.retryLater()).isTrue();
        assertThat(result.error()).isEqualTo(QueueSkipError.IDEMPOTENCY_IN_PROGRESS);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void failedSameHashRequiresNewIdempotencyKey() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        String hash = QueueSkipApplicationService.requestHash(scenario.command());
        scenario.idempotencyRepository.existing = idempotencyRecord(hash, IdempotencyStatus.FAILED, "{\"failure_reason\":\"audit_write_failed\"}");

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(QueueSkipError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void sameKeyDifferentHashReturnsConflict() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        scenario.idempotencyRepository.existing = completedRecord("different-hash", scenario);

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(QueueSkipError.IDEMPOTENCY_CONFLICT);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void queueTicketNotFoundReturnsApplicationError() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        scenario.queueTicketRepository.persisted.clear();

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.command());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(QueueSkipError.QUEUE_TICKET_NOT_FOUND);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void nonCalledQueueTicketStatusesAreRejected() {
        assertQueueStatusRejected(QueueTicketStatus.WAITING);
        assertQueueStatusRejected(QueueTicketStatus.REJOINED);
        assertQueueStatusRejected(QueueTicketStatus.SEATED);
        assertQueueStatusRejected(QueueTicketStatus.CANCELLED);
        assertQueueStatusRejected(QueueTicketStatus.EXPIRED);
    }

    @Test
    void relatedReservationMustExistAndRemainArrived() {
        Scenario missingReservation = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        missingReservation.reservationRepository.reservations.clear();

        QueueSkipResult missingResult = missingReservation.service().skipQueueTicket(missingReservation.command());

        assertThat(missingResult.success()).isFalse();
        assertThat(missingResult.error()).isEqualTo(QueueSkipError.RESERVATION_NOT_FOUND);
        assertThat(missingReservation.queueTicketRepository.saved).isEmpty();

        Scenario notArrived = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.CONFIRMED);

        QueueSkipResult notArrivedResult = notArrived.service().skipQueueTicket(notArrived.command());

        assertThat(notArrivedResult.success()).isFalse();
        assertThat(notArrivedResult.error()).isEqualTo(QueueSkipError.RESERVATION_STATUS_NOT_ARRIVED);
        assertThat(notArrived.reservationRepository.saved).isEmpty();
        assertThat(notArrived.queueTicketRepository.saved).isEmpty();
    }

    @Test
    void walkInQueueTicketCanBeSkippedWithoutReservation() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        scenario.queueTicketRepository.persisted.put(
            scenario.queueTicketId.value(),
            scenario.queueTicketWithoutReservation()
        );

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.commandWithKey("idem-no-reservation"));

        assertThat(result.success()).isTrue();
        assertThat(result.reservationId()).isNull();
        assertThat(result.reservationCode()).isNull();
        assertThat(result.reservationStatus()).isNull();
        assertThat(scenario.queueTicketRepository.saved).hasSize(1);
        assertThat(scenario.queueTicketRepository.saved.getFirst().status()).isEqualTo(QueueTicketStatus.SKIPPED);
    }

    @Test
    void eventTransitionAuditAndPersistenceFailuresReturnApplicationErrors() {
        assertWriteFailure(scenario -> scenario.businessEventRepository.failOnAppend = true, QueueSkipError.BUSINESS_EVENT_WRITE_FAILED);
        assertWriteFailure(scenario -> scenario.stateTransitionLogRepository.failOnAppend = true, QueueSkipError.STATE_TRANSITION_WRITE_FAILED);
        assertWriteFailure(scenario -> scenario.auditLogRepository.failOnAppend = true, QueueSkipError.AUDIT_WRITE_FAILED);
        assertWriteFailure(scenario -> scenario.queueTicketRepository.failOnSave = true, QueueSkipError.PERSISTENCE_ERROR);
    }

    @Test
    void queueTicketMapperPreservesSkippedAtBothWays() {
        DefaultQueueTicketMapper mapper = new DefaultQueueTicketMapper();
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID queueTicketId = UUID.randomUUID();
        Instant skippedAt = Instant.parse("2026-06-20T03:50:00Z");

        QueueTicketEntity entity = QueueTicketEntity.of(
            queueTicketId,
            tenantId,
            storeId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            27,
            4,
            LocalDate.of(2026, 6, 20),
            "skipped",
            2,
            OffsetDateTime.parse("2026-06-20T03:30:00Z"),
            OffsetDateTime.ofInstant(skippedAt, ZoneOffset.UTC),
            null,
            OffsetDateTime.parse("2026-06-20T03:33:00Z"),
            null,
            "ticket note",
            OffsetDateTime.parse("2026-06-20T02:00:00Z"),
            OffsetDateTime.parse("2026-06-20T03:50:00Z"),
            null,
            3
        );

        QueueTicket domain = mapper.toDomain(entity);
        QueueTicketEntity mappedBack = mapper.toEntity(domain);

        assertThat(domain.skippedAt()).isEqualTo(skippedAt);
        assertThat(mappedBack.getSkippedAt()).isEqualTo(OffsetDateTime.ofInstant(skippedAt, ZoneOffset.UTC));
    }

    @Test
    void boundaryDoesNotCreateSeatingTableCleaningTurnoverNoShowCancellationApiUiOrMigration() {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.commandWithKey("idem-boundary"));

        assertThat(result.success()).isTrue();
        assertBoundaryUnchanged(scenario);
    }

    private static void assertQueueStatusRejected(QueueTicketStatus status) {
        Scenario scenario = Scenario.ready(status, ReservationStatus.ARRIVED);

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.commandWithKey("idem-" + status.code()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(QueueSkipError.QUEUE_TICKET_STATUS_NOT_CALLED);
        assertThat(scenario.queueTicketRepository.saved).isEmpty();
        assertThat(scenario.businessEventRepository.events).isEmpty();
        assertThat(scenario.stateTransitionLogRepository.logs).isEmpty();
    }

    private static void assertWriteFailure(java.util.function.Consumer<Scenario> mutator, QueueSkipError expectedError) {
        Scenario scenario = Scenario.ready(QueueTicketStatus.CALLED, ReservationStatus.ARRIVED);
        mutator.accept(scenario);

        QueueSkipResult result = scenario.service().skipQueueTicket(scenario.commandWithKey("idem-failure-" + expectedError.code()));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo(expectedError);
        assertThat(scenario.idempotencyRepository.failed).hasSize(1);
    }

    private static void assertBoundaryUnchanged(Scenario scenario) {
        assertThat(scenario.seatingCreated).isFalse();
        assertThat(scenario.seatingResourceCreated).isFalse();
        assertThat(scenario.tableStatusChanged).isFalse();
        assertThat(scenario.queueRejoinImplemented).isFalse();
        assertThat(scenario.queueDisplayImplemented).isFalse();
        assertThat(scenario.queueWorkbenchImplemented).isFalse();
        assertThat(scenario.tableMapImplemented).isFalse();
        assertThat(scenario.autoAssignmentImplemented).isFalse();
        assertThat(scenario.cleaningCreated).isFalse();
        assertThat(scenario.turnoverCreated).isFalse();
        assertThat(scenario.noShowImplemented).isFalse();
        assertThat(scenario.cancellationImplemented).isFalse();
        assertThat(scenario.controllerCreated).isFalse();
        assertThat(scenario.apiDtoCreated).isFalse();
        assertThat(scenario.uiCreated).isFalse();
        assertThat(scenario.migrationChanged).isFalse();
        assertThat(scenario.appGateMetadataChanged).isFalse();
    }

    private static IdempotencyRecord completedRecord(String requestHash, Scenario scenario) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-skip-ticket"),
            "staff",
            "skip_queue_ticket",
            requestHash,
            IdempotencyStatus.COMPLETED,
            "queue_ticket",
            scenario.queueTicketId.value(),
            """
                {"queueTicketId":"%s","queueTicketNumber":27,"queueTicketStatus":"skipped","reservationId":"%s","reservationCode":"R-SKIP-1","reservationStatus":"arrived","skippedAt":"2026-06-20T03:40:00Z","alreadySkipped":false}
                """.formatted(scenario.queueTicketId.value(), scenario.reservationId.value()).trim()
        );
    }

    private static IdempotencyRecord idempotencyRecord(String requestHash, IdempotencyStatus status, String snapshot) {
        return new IdempotencyRecord(
            UUID.randomUUID(),
            new IdempotencyKey("idem-skip-ticket"),
            "staff",
            "skip_queue_ticket",
            requestHash,
            status,
            null,
            null,
            snapshot
        );
    }

    private static final class Scenario {
        final Instant now = Instant.parse("2026-06-20T03:35:00Z");
        final Instant calledAt = Instant.parse("2026-06-20T03:30:00Z");
        final Instant expiresAt = Instant.parse("2026-06-20T03:33:00Z");
        final TenantId tenantId = new TenantId(UUID.randomUUID());
        final StoreId storeId = new StoreId(UUID.randomUUID());
        final StoreScope scope = new StoreScope(tenantId, storeId);
        final UUID actorId = UUID.randomUUID();
        final QueueTicketId queueTicketId = new QueueTicketId(UUID.randomUUID());
        final UUID queueGroupId = UUID.randomUUID();
        final ReservationId reservationId = new ReservationId(UUID.randomUUID());
        final CustomerId customerId = new CustomerId(UUID.randomUUID());
        final Store store = new Store(storeId, tenantId, "STORE-1", "Asia/Singapore", "zh-CN", "active");
        final FakeStoreRepository storeRepository = new FakeStoreRepository(this);
        final FakeReservationRepository reservationRepository = new FakeReservationRepository();
        final FakeQueueTicketRepository queueTicketRepository = new FakeQueueTicketRepository();
        final FakeBusinessEventRepository businessEventRepository = new FakeBusinessEventRepository();
        final FakeStateTransitionLogRepository stateTransitionLogRepository = new FakeStateTransitionLogRepository();
        final FakeAuditLogRepository auditLogRepository = new FakeAuditLogRepository();
        final FakeIdempotencyRepository idempotencyRepository = new FakeIdempotencyRepository();
        boolean seatingCreated;
        boolean seatingResourceCreated;
        boolean tableStatusChanged;
        boolean queueRejoinImplemented;
        boolean queueDisplayImplemented;
        boolean queueWorkbenchImplemented;
        boolean tableMapImplemented;
        boolean autoAssignmentImplemented;
        boolean cleaningCreated;
        boolean turnoverCreated;
        boolean noShowImplemented;
        boolean cancellationImplemented;
        boolean controllerCreated;
        boolean apiDtoCreated;
        boolean uiCreated;
        boolean migrationChanged;
        boolean appGateMetadataChanged;

        static Scenario ready(QueueTicketStatus ticketStatus, ReservationStatus reservationStatus) {
            Scenario scenario = new Scenario();
            scenario.queueTicketRepository.persisted.put(
                scenario.queueTicketId.value(),
                scenario.queueTicket(ticketStatus, scenario.calledAt, scenario.expiresAt, null)
            );
            scenario.reservationRepository.reservations.put(
                scenario.reservationId.value(),
                scenario.reservation(reservationStatus)
            );
            return scenario;
        }

        QueueSkipApplicationService service() {
            return new QueueSkipApplicationService(
                storeRepository,
                queueTicketRepository,
                reservationRepository,
                businessEventRepository,
                stateTransitionLogRepository,
                auditLogRepository,
                idempotencyRepository,
                Clock.fixed(now, ZoneOffset.UTC)
            );
        }

        SkipQueueTicketCommand command() {
            return commandWithKey("idem-skip-ticket");
        }

        SkipQueueTicketCommand commandWithKey(String idempotencyKey) {
            return commandWithSkippedAt(null, idempotencyKey);
        }

        SkipQueueTicketCommand commandWithSkippedAt(Instant skippedAt, String idempotencyKey) {
            return new SkipQueueTicketCommand(
                tenantId.value(),
                storeId.value(),
                queueTicketId.value(),
                skippedAt,
                "no_response",
                "Customer did not respond",
                idempotencyKey,
                actorId,
                "staff"
            );
        }

        Reservation reservation(ReservationStatus status) {
            return new Reservation(
                reservationId,
                scope,
                customerId,
                new ReservationCode("R-SKIP-1"),
                new PartySize(4),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                Instant.parse("2026-06-20T03:00:00Z"),
                Instant.parse("2026-06-20T04:30:00Z"),
                Instant.parse("2026-06-20T03:15:00Z"),
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

        QueueTicket queueTicket(QueueTicketStatus status, Instant calledAt, Instant expiresAt, Instant skippedAt) {
            return new QueueTicket(
                queueTicketId,
                scope,
                queueGroupId,
                customerId,
                reservationId.value(),
                null,
                new QueueTicketNumber(27),
                new PartySize(4),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                status,
                2,
                calledAt,
                expiresAt,
                skippedAt,
                "ticket note"
            );
        }

        QueueTicket queueTicketWithoutReservation() {
            return new QueueTicket(
                queueTicketId,
                scope,
                queueGroupId,
                customerId,
                null,
                null,
                new QueueTicketNumber(27),
                new PartySize(4),
                new BusinessDate(LocalDate.of(2026, 6, 20)),
                QueueTicketStatus.CALLED,
                2,
                calledAt,
                expiresAt,
                null,
                "ticket note"
            );
        }

        void persistSkipEvidence() {
            businessEventRepository.events.add(new BusinessEvent(
                UUID.randomUUID(),
                "queue_ticket.skipped",
                "queue_ticket",
                queueTicketId.value(),
                "staff",
                actorId,
                "staff",
                "{}"
            ));
            stateTransitionLogRepository.logs.add(new StateTransitionLog(
                UUID.randomUUID(),
                "queue_ticket",
                queueTicketId.value(),
                "called",
                "skipped",
                "queue_ticket.skip",
                "staff",
                actorId,
                "staff",
                "{}"
            ));
            auditLogRepository.logs.add(new AuditLog(
                UUID.randomUUID(),
                "queue.skip",
                "queue_ticket",
                queueTicketId.value(),
                "staff",
                "staff",
                actorId,
                "{}"
            ));
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
            return Optional.empty();
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

    private static final class FakeQueueTicketRepository implements QueueTicketRepositoryPort {
        final Map<UUID, QueueTicket> persisted = new HashMap<>();
        final List<QueueTicket> saved = new ArrayList<>();
        boolean failOnSave;

        @Override
        public Optional<QueueTicket> findById(StoreScope scope, QueueTicketId queueTicketId) {
            return Optional.ofNullable(persisted.get(queueTicketId.value()))
                .filter(ticket -> ticket.scope().equals(scope));
        }

        @Override
        public List<QueueTicket> findActiveQueue(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return List.of();
        }

        @Override
        public Optional<QueueTicket> findNextCallable(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
            return Optional.empty();
        }

        @Override
        public Optional<QueueTicket> findActiveByReservationId(StoreScope scope, UUID reservationId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveSourceTicket(StoreScope scope, String sourceType, UUID sourceId) {
            return false;
        }

        @Override
        public QueueTicket save(StoreScope scope, QueueTicket queueTicket) {
            if (failOnSave) {
                throw new IllegalStateException("queue ticket save failed");
            }
            saved.add(queueTicket);
            persisted.put(queueTicket.id().value(), queueTicket);
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
                requestHash(record),
                IdempotencyStatus.FAILED,
                record.targetType(),
                record.targetId(),
                "{\"failure_reason\":\"" + failureReason + "\"}"
            );
            failed.add(failedRecord);
            existing = failedRecord;
            return failedRecord;
        }

        private static String requestHash(IdempotencyRecord record) {
            return record.requestHash();
        }
    }
}
