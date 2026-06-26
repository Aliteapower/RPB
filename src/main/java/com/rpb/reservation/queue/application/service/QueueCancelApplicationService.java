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
import com.rpb.reservation.queue.application.QueueCancelError;
import com.rpb.reservation.queue.application.QueueCancelResult;
import com.rpb.reservation.queue.application.command.CancelQueueTicketCommand;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.state.QueueTicketStateMachine;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
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
public class QueueCancelApplicationService {

    private static final String ACTION = "cancel_queue_ticket";
    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_QUEUE_TICKET_CANCELLED = "queue_ticket.cancelled";
    private static final String OPERATION_CANCEL = "queue.cancel";
    private static final String OPERATION_CANCEL_FAILED = "queue.cancel.failed";
    private static final String TRANSITION_QUEUE_TICKET_CANCEL = "queue_ticket.cancel";

    private final StoreRepositoryPort storeRepository;
    private final QueueTicketRepositoryPort queueTicketRepository;
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

    @Autowired
    public QueueCancelApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this(
            storeRepository,
            queueTicketRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            Clock.systemUTC()
        );
    }

    public QueueCancelApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional
    public QueueCancelResult cancelQueueTicket(CancelQueueTicketCommand command) {
        QueueCancelError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return QueueCancelResult.failure(preValidationError);
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
            return QueueCancelResult.failure(QueueCancelError.PERSISTENCE_ERROR);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? QueueCancelResult.retryLater(failure.error())
                : QueueCancelResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, QueueCancelError.PERSISTENCE_ERROR);
            appendFailureAudit(scope, command, started.idempotencyKey(), QueueCancelError.PERSISTENCE_ERROR);
            return QueueCancelResult.failure(QueueCancelError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(CancelQueueTicketCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.queueTicketId()),
            value(command.actorId()),
            normalize(command.actorType()),
            command.cancelledAt() == null ? "application_clock" : value(command.cancelledAt()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private QueueCancelResult execute(CancelQueueTicketCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(QueueCancelError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(QueueCancelError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), QueueCancelError.STORE_ACCESS_DENIED);

        QueueTicket queueTicket = queueTicketRepository.findById(scope, new QueueTicketId(command.queueTicketId()))
            .orElseThrow(() -> new ApplicationFailure(QueueCancelError.QUEUE_TICKET_NOT_FOUND));
        if (!scope.equals(queueTicket.scope())) {
            throw new ApplicationFailure(QueueCancelError.STORE_SCOPE_MISMATCH);
        }

        Instant cancelledAt = command.cancelledAt() == null ? Instant.now(clock) : command.cancelledAt();
        String reasonCode = blankToNull(command.reasonCode());

        if (queueTicket.status() == QueueTicketStatus.CANCELLED) {
            IdempotencyRecord completed = completeIdempotency(scope, started, queueTicket, cancelledAt, reasonCode, true);
            return QueueCancelResult.alreadyCancelled(
                queueTicket.id().value(),
                queueTicket.ticketNumber().value(),
                queueTicket.reservationId(),
                null,
                null,
                queueTicket.walkInId(),
                cancelledAt,
                reasonCode,
                completed.status().code()
            );
        }
        if (queueTicket.status() == QueueTicketStatus.SEATED) {
            throw new ApplicationFailure(QueueCancelError.QUEUE_TICKET_CANNOT_CANCEL_SEATED);
        }
        if (queueTicket.status() == QueueTicketStatus.EXPIRED) {
            throw new ApplicationFailure(QueueCancelError.QUEUE_TICKET_CANNOT_CANCEL_EXPIRED);
        }
        if (!queueTicketStateMachine.canTransition(queueTicket.status(), QueueTicketStatus.CANCELLED)) {
            throw new ApplicationFailure(QueueCancelError.ILLEGAL_STATE_TRANSITION);
        }

        QueueTicket beforeTicket = queueTicket;
        QueueTicket savedTicket = saveCancelled(scope, queueTicket.cancel());
        BusinessEvent event = appendBusinessEvent(scope, command, beforeTicket, savedTicket, cancelledAt, reasonCode, started.idempotencyKey(), false);
        StateTransitionLog transition = appendTransitionLog(scope, command, beforeTicket, savedTicket, cancelledAt, reasonCode, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, beforeTicket, savedTicket, cancelledAt, reasonCode, started.idempotencyKey());
        IdempotencyRecord completed = completeIdempotency(scope, started, savedTicket, cancelledAt, reasonCode, false);

        return QueueCancelResult.success(
            savedTicket.id().value(),
            savedTicket.ticketNumber().value(),
            savedTicket.reservationId(),
            null,
            null,
            savedTicket.walkInId(),
            cancelledAt,
            reasonCode,
            completed.status().code(),
            List.of(EVENT_QUEUE_TICKET_CANCELLED),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private QueueTicket saveCancelled(StoreScope scope, QueueTicket queueTicket) {
        try {
            return queueTicketRepository.save(scope, queueTicket);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCancelError.PERSISTENCE_ERROR, exception);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        CancelQueueTicketCommand command,
        QueueTicket beforeTicket,
        QueueTicket queueTicket,
        Instant cancelledAt,
        String reasonCode,
        IdempotencyKey idempotencyKey,
        boolean alreadyCancelled
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_QUEUE_TICKET_CANCELLED,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(beforeTicket, queueTicket, command, cancelledAt, reasonCode, idempotencyKey, alreadyCancelled)
        );
        require(
            businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
            QueueCancelError.BUSINESS_EVENT_WRITE_FAILED
        );
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCancelError.BUSINESS_EVENT_WRITE_FAILED, exception);
        }
    }

    private StateTransitionLog appendTransitionLog(
        StoreScope scope,
        CancelQueueTicketCommand command,
        QueueTicket beforeTicket,
        QueueTicket queueTicket,
        Instant cancelledAt,
        String reasonCode,
        IdempotencyKey idempotencyKey
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            beforeTicket.status().code(),
            QueueTicketStatus.CANCELLED.code(),
            TRANSITION_QUEUE_TICKET_CANCEL,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(beforeTicket, queueTicket, command, cancelledAt, reasonCode, idempotencyKey, false)
        );
        require(
            stateTransitionRule.evaluate(transition.targetType(), transition.targetId(), transition.toStatus(), transition.transitionCode(), transition.actorType()),
            QueueCancelError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return stateTransitionLogRepository.append(scope, transition);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCancelError.STATE_TRANSITION_WRITE_FAILED, exception);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        CancelQueueTicketCommand command,
        QueueTicket beforeTicket,
        QueueTicket queueTicket,
        Instant cancelledAt,
        String reasonCode,
        IdempotencyKey idempotencyKey
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_CANCEL,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(beforeTicket, queueTicket, command, cancelledAt, reasonCode, idempotencyKey, false)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), QueueCancelError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCancelError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        CancelQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        QueueCancelError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_CANCEL_FAILED,
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
        Instant cancelledAt,
        String reasonCode,
        boolean alreadyCancelled
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
            snapshot(queueTicket, cancelledAt, reasonCode, alreadyCancelled)
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, TARGET_QUEUE_TICKET);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCancelError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueCancelResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            QueueCancelError error = QueueCancelError.fromCode(decision.violationCode());
            if (error == QueueCancelError.IDEMPOTENCY_IN_PROGRESS) {
                return QueueCancelResult.retryLater(error);
            }
            return QueueCancelResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return QueueCancelResult.failure(QueueCancelError.IDEMPOTENCY_CONFLICT);
            }
        }
        return QueueCancelResult.failure(QueueCancelError.IDEMPOTENCY_CONFLICT);
    }

    private QueueCancelResult replay(String snapshot) {
        return QueueCancelResult.replay(
            UUID.fromString(extract(snapshot, "queueTicketId")),
            Integer.parseInt(extractNumber(snapshot, "queueTicketNumber")),
            extractNullableUuid(snapshot, "reservationId"),
            extractNullableString(snapshot, "reservationCode"),
            extractNullableString(snapshot, "reservationStatus"),
            extractNullableUuid(snapshot, "walkInId"),
            Instant.parse(extract(snapshot, "cancelledAt")),
            extractNullableString(snapshot, "cancellationReasonCode"),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyCancelled"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, QueueCancelError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static QueueCancelError validateCommand(CancelQueueTicketCommand command) {
        if (command == null) {
            return QueueCancelError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return QueueCancelError.MISSING_IDEMPOTENCY_KEY;
        }
        if (command.tenantId() == null || command.storeId() == null || command.queueTicketId() == null || command.actorId() == null || !hasText(command.actorType())) {
            return QueueCancelError.INVALID_COMMAND;
        }
        return null;
    }

    private static void require(RuleDecision decision, QueueCancelError fallback) {
        if (!decision.accepted()) {
            QueueCancelError error = QueueCancelError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == QueueCancelError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(QueueTicket queueTicket, Instant cancelledAt, String reasonCode, boolean alreadyCancelled) {
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"cancelled","reservationId":%s,"reservationCode":null,"reservationStatus":null,"walkInId":%s,"cancelledAt":"%s","cancellationReasonCode":%s,"alreadyCancelled":%s}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            jsonNullable(queueTicket.reservationId()),
            jsonNullable(queueTicket.walkInId()),
            cancelledAt,
            jsonNullable(reasonCode),
            alreadyCancelled
        ).trim();
    }

    private static String metadata(
        QueueTicket beforeTicket,
        QueueTicket queueTicket,
        CancelQueueTicketCommand command,
        Instant cancelledAt,
        String reasonCode,
        IdempotencyKey idempotencyKey,
        boolean alreadyCancelled
    ) {
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"beforeQueueTicketStatus":"%s","afterQueueTicketStatus":"cancelled","reservationId":%s,"walkInId":%s,"queueGroupId":"%s","businessDate":"%s","partySize":%d,"queuePosition":%s,"cancelledAt":"%s","alreadyCancelled":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            beforeTicket.status().code(),
            jsonNullable(queueTicket.reservationId()),
            jsonNullable(queueTicket.walkInId()),
            queueTicket.queueGroupId(),
            queueTicket.businessDate().value(),
            queueTicket.partySize().value(),
            queueTicket.queuePosition() == null ? "null" : queueTicket.queuePosition(),
            cancelledAt,
            alreadyCancelled,
            jsonNullable(reasonCode),
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

    private static String source(CancelQueueTicketCommand command) {
        return OperationSource.fromActorType(command == null ? null : command.actorType());
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
    }

    private static String jsonNullable(UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
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
        private final QueueCancelError error;
        private final boolean retryLater;

        private ApplicationFailure(QueueCancelError error) {
            this(error, false, null);
        }

        private ApplicationFailure(QueueCancelError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(QueueCancelError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(QueueCancelError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private QueueCancelError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
