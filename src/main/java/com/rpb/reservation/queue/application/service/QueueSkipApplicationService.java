package com.rpb.reservation.queue.application.service;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.audit.rule.DefaultAuditRule;
import com.rpb.reservation.audit.rule.DefaultBusinessEventRule;
import com.rpb.reservation.audit.rule.DefaultStateTransitionRule;
import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.DefaultStoreAccessPolicy;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.queue.application.QueueSkipError;
import com.rpb.reservation.queue.application.QueueSkipResult;
import com.rpb.reservation.queue.application.command.SkipQueueTicketCommand;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.rule.QueueSkipEvidenceRule;
import com.rpb.reservation.queue.application.rule.QueueSkipRule;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.state.QueueTicketStateMachine;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueSkipApplicationService {

    private static final String ACTION = "skip_queue_ticket";
    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_QUEUE_TICKET_SKIPPED = "queue_ticket.skipped";
    private static final String OPERATION_SKIP = "queue.skip";
    private static final String OPERATION_SKIP_FAILED = "queue.skip.failed";
    private static final String TRANSITION_QUEUE_TICKET_SKIP = "queue_ticket.skip";

    private final StoreRepositoryPort storeRepository;
    private final QueueTicketRepositoryPort queueTicketRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final BusinessEventRepositoryPort businessEventRepository;
    private final StateTransitionLogRepositoryPort stateTransitionLogRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final IdempotencyRepositoryPort idempotencyRepository;
    private final Clock clock;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();
    private final DefaultAuditRule auditRule = new DefaultAuditRule();
    private final DefaultBusinessEventRule businessEventRule = new DefaultBusinessEventRule();
    private final DefaultStateTransitionRule stateTransitionRule = new DefaultStateTransitionRule();
    private final DefaultIdempotencyRule idempotencyRule = new DefaultIdempotencyRule();
    private final QueueTicketStateMachine queueTicketStateMachine = new QueueTicketStateMachine();
    private final QueueSkipRule queueSkipRule = new QueueSkipRule();
    private final QueueSkipEvidenceRule queueSkipEvidenceRule = new QueueSkipEvidenceRule();

    @Autowired
    public QueueSkipApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        ReservationRepositoryPort reservationRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this(
            storeRepository,
            queueTicketRepository,
            reservationRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            Clock.systemUTC()
        );
    }

    public QueueSkipApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        ReservationRepositoryPort reservationRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.reservationRepository = reservationRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional
    public QueueSkipResult skipQueueTicket(SkipQueueTicketCommand command) {
        QueueSkipError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return QueueSkipResult.failure(preValidationError);
        }

        StoreScope scope = new StoreScope(new TenantId(command.tenantId()), command.storeId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String requestHash = requestHash(command);
        String source = source(command);

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByScopeActionKey(scope, source, ACTION, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExistingIdempotency(existing.get(), requestHash);
        }

        IdempotencyRecord started;
        try {
            started = idempotencyRepository.start(scope, source, ACTION, idempotencyKey, requestHash, OffsetDateTime.now(clock).plusMinutes(30));
        } catch (RuntimeException exception) {
            return QueueSkipResult.failure(QueueSkipError.PERSISTENCE_ERROR);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? QueueSkipResult.retryLater(failure.error())
                : QueueSkipResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, QueueSkipError.PERSISTENCE_ERROR);
            appendFailureAudit(scope, command, started.idempotencyKey(), QueueSkipError.PERSISTENCE_ERROR);
            return QueueSkipResult.failure(QueueSkipError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(SkipQueueTicketCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.queueTicketId()),
            value(command.actorId()),
            normalize(command.actorType()),
            command.skippedAt() == null ? "application_clock" : value(command.skippedAt()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private QueueSkipResult execute(SkipQueueTicketCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(QueueSkipError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(QueueSkipError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), QueueSkipError.STORE_ACCESS_DENIED);

        QueueTicket queueTicket = queueTicketRepository.findById(scope, new QueueTicketId(command.queueTicketId()))
            .orElseThrow(() -> new ApplicationFailure(QueueSkipError.QUEUE_TICKET_NOT_FOUND));
        if (!scope.equals(queueTicket.scope())) {
            throw new ApplicationFailure(QueueSkipError.STORE_SCOPE_MISMATCH);
        }

        Optional<Reservation> reservation = loadReservationIfPresent(scope, queueTicket);

        if (queueTicket.status() == QueueTicketStatus.SKIPPED) {
            return alreadySkipped(scope, started, queueTicket, reservation);
        }

        QueueSkipError statusError = queueSkipRule.validateFreshSkip(queueTicket.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }
        if (!queueTicketStateMachine.canTransition(QueueTicketStatus.CALLED, QueueTicketStatus.SKIPPED)) {
            throw new ApplicationFailure(QueueSkipError.ILLEGAL_STATE_TRANSITION);
        }

        Instant skippedAt = command.skippedAt() == null ? Instant.now(clock) : command.skippedAt();
        QueueTicket savedTicket = saveSkipped(scope, queueTicket.skip(skippedAt));
        BusinessEvent event = appendBusinessEvent(scope, command, savedTicket, reservation, started.idempotencyKey(), false);
        StateTransitionLog transition = appendTransitionLog(scope, command, savedTicket, reservation, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, savedTicket, reservation, started.idempotencyKey());
        IdempotencyRecord completed = completeIdempotency(scope, started, savedTicket, reservation, false);

        return QueueSkipResult.success(
            savedTicket.id().value(),
            savedTicket.ticketNumber().value(),
            reservation.map(value -> value.id().value()).orElse(null),
            reservation.map(value -> value.reservationCode().value()).orElse(null),
            savedTicket.skippedAt(),
            completed.status().code(),
            List.of(EVENT_QUEUE_TICKET_SKIPPED),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private QueueSkipResult alreadySkipped(
        StoreScope scope,
        IdempotencyRecord started,
        QueueTicket queueTicket,
        Optional<Reservation> reservation
    ) {
        QueueSkipError evidenceError = queueSkipEvidenceRule.validateAlreadySkippedEvidence(
            queueTicket,
            businessEventRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value()),
            stateTransitionLogRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value()),
            auditLogRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value())
        );
        if (evidenceError != null) {
            throw new ApplicationFailure(evidenceError);
        }
        IdempotencyRecord completed = completeIdempotency(scope, started, queueTicket, reservation, true);
        return QueueSkipResult.alreadySkipped(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            reservation.map(value -> value.id().value()).orElse(null),
            reservation.map(value -> value.reservationCode().value()).orElse(null),
            queueTicket.skippedAt(),
            completed.status().code()
        );
    }

    private Optional<Reservation> loadReservationIfPresent(StoreScope scope, QueueTicket queueTicket) {
        if (queueTicket.reservationId() == null) {
            return Optional.empty();
        }
        Reservation reservation = reservationRepository.findById(scope, new ReservationId(queueTicket.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(QueueSkipError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(QueueSkipError.STORE_SCOPE_MISMATCH);
        }
        if (reservation.status() != ReservationStatus.ARRIVED) {
            throw new ApplicationFailure(QueueSkipError.RESERVATION_STATUS_NOT_ARRIVED);
        }
        return Optional.of(reservation);
    }

    private QueueTicket saveSkipped(StoreScope scope, QueueTicket queueTicket) {
        try {
            return queueTicketRepository.save(scope, queueTicket);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueSkipError.PERSISTENCE_ERROR, exception);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        SkipQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        IdempotencyKey idempotencyKey,
        boolean alreadySkipped
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_QUEUE_TICKET_SKIPPED,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(queueTicket, reservation, command, idempotencyKey, alreadySkipped)
        );
        require(
            businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
            QueueSkipError.BUSINESS_EVENT_WRITE_FAILED
        );
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueSkipError.BUSINESS_EVENT_WRITE_FAILED, exception);
        }
    }

    private StateTransitionLog appendTransitionLog(
        StoreScope scope,
        SkipQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        IdempotencyKey idempotencyKey
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            QueueTicketStatus.CALLED.code(),
            QueueTicketStatus.SKIPPED.code(),
            TRANSITION_QUEUE_TICKET_SKIP,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(queueTicket, reservation, command, idempotencyKey, false)
        );
        require(
            stateTransitionRule.evaluate(
                transition.targetType(),
                transition.targetId(),
                transition.toStatus(),
                transition.transitionCode(),
                transition.actorType()
            ),
            QueueSkipError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return stateTransitionLogRepository.append(scope, transition);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueSkipError.STATE_TRANSITION_WRITE_FAILED, exception);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        SkipQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        IdempotencyKey idempotencyKey
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_SKIP,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(queueTicket, reservation, command, idempotencyKey, false)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), QueueSkipError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueSkipError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        SkipQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        QueueSkipError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_SKIP_FAILED,
                    TARGET_QUEUE_TICKET,
                    command.queueTicketId(),
                    source(command),
                    command.actorType(),
                    command.actorId(),
                    """
                        {"failureReason":"%s","queueTicketId":"%s","reasonCode":%s,"idempotencyKey":"%s"}
                        """.formatted(
                        error.code(),
                        command.queueTicketId(),
                        jsonNullable(command.reasonCode()),
                        escape(idempotencyKey.value())
                    ).trim()
                )
            );
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private IdempotencyRecord completeIdempotency(
        StoreScope scope,
        IdempotencyRecord started,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        boolean alreadySkipped
    ) {
        IdempotencyRecord completionPayload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            snapshot(queueTicket, reservation, alreadySkipped)
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, TARGET_QUEUE_TICKET);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueSkipError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueSkipResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            QueueSkipError error = QueueSkipError.fromCode(decision.violationCode());
            if (error == QueueSkipError.IDEMPOTENCY_IN_PROGRESS) {
                return QueueSkipResult.retryLater(error);
            }
            return QueueSkipResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return QueueSkipResult.failure(QueueSkipError.IDEMPOTENCY_CONFLICT);
            }
        }
        return QueueSkipResult.failure(QueueSkipError.IDEMPOTENCY_CONFLICT);
    }

    private QueueSkipResult replay(String snapshot) {
        return QueueSkipResult.replay(
            UUID.fromString(extract(snapshot, "queueTicketId")),
            Integer.parseInt(extractNumber(snapshot, "queueTicketNumber")),
            extract(snapshot, "queueTicketStatus"),
            extractNullableUuid(snapshot, "reservationId"),
            extractNullableString(snapshot, "reservationCode"),
            extractNullableString(snapshot, "reservationStatus"),
            Instant.parse(extract(snapshot, "skippedAt")),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadySkipped"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, QueueSkipError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static QueueSkipError validateCommand(SkipQueueTicketCommand command) {
        if (command == null) {
            return QueueSkipError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return QueueSkipError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.queueTicketId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return QueueSkipError.INVALID_COMMAND;
        }
        return null;
    }

    private static void require(RuleDecision decision, QueueSkipError fallback) {
        if (!decision.accepted()) {
            QueueSkipError error = QueueSkipError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == QueueSkipError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(QueueTicket queueTicket, Optional<Reservation> reservation, boolean alreadySkipped) {
        String reservationId = jsonNullable(reservation.map(value -> value.id().value()).orElse(null));
        String reservationCode = jsonNullable(reservation.map(value -> value.reservationCode().value()).orElse(null));
        String reservationStatus = jsonNullable(reservation.map(value -> value.status().code()).orElse(null));
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"skipped","reservationId":%s,"reservationCode":%s,"reservationStatus":%s,"skippedAt":"%s","alreadySkipped":%s}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            reservationId,
            reservationCode,
            reservationStatus,
            queueTicket.skippedAt(),
            alreadySkipped
        ).trim();
    }

    private static String metadata(
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        SkipQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        boolean alreadySkipped
    ) {
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"beforeQueueTicketStatus":"called","afterQueueTicketStatus":"skipped","reservationId":%s,"reservationCode":%s,"reservationStatus":%s,"queueGroupId":"%s","businessDate":"%s","partySize":%d,"queuePosition":%s,"calledAt":%s,"holdUntilAt":%s,"skippedAt":"%s","alreadySkipped":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            jsonNullable(reservation.map(value -> value.id().value()).orElse(null)),
            jsonNullable(reservation.map(value -> value.reservationCode().value()).orElse(null)),
            jsonNullable(reservation.map(value -> value.status().code()).orElse(null)),
            queueTicket.queueGroupId(),
            queueTicket.businessDate().value(),
            queueTicket.partySize().value(),
            queueTicket.queuePosition() == null ? "null" : queueTicket.queuePosition(),
            jsonNullable(queueTicket.calledAt()),
            jsonNullable(queueTicket.expiresAt()),
            queueTicket.skippedAt(),
            alreadySkipped,
            jsonNullable(command.reasonCode()),
            jsonNullable(command.note()),
            escape(idempotencyKey.value())
        ).trim();
    }

    private static String extract(String json, String key) {
        if (json == null) {
            throw new IllegalArgumentException("snapshot_missing");
        }
        Pattern quoted = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = quoted.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String extractNullableString(String json, String key) {
        Pattern nullPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*null");
        if (nullPattern.matcher(json).find()) {
            return null;
        }
        return extract(json, key);
    }

    private static UUID extractNullableUuid(String json, String key) {
        String value = extractNullableString(json, key);
        return value == null ? null : UUID.fromString(value);
    }

    private static String extractNumber(String json, String key) {
        Pattern number = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = number.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String extractBoolean(String json, String key) {
        Pattern bool = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = bool.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String source(SkipQueueTicketCommand command) {
        return OperationSource.fromActorType(command == null ? null : command.actorType());
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
    }

    private static String jsonNullable(UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String jsonNullable(Instant value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha256(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha_256_unavailable", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final QueueSkipError error;
        private final boolean retryLater;

        private ApplicationFailure(QueueSkipError error) {
            this(error, false, null);
        }

        private ApplicationFailure(QueueSkipError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(QueueSkipError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(QueueSkipError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private QueueSkipError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
