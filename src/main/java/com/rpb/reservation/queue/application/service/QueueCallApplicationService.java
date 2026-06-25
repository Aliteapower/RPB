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
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.queue.application.QueueCallError;
import com.rpb.reservation.queue.application.QueueCallResult;
import com.rpb.reservation.queue.application.command.CallQueueTicketCommand;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.rule.QueueCallHoldPolicy;
import com.rpb.reservation.queue.application.rule.QueueCallRule;
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
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class QueueCallApplicationService {

    private static final String ACTION = "call_queue_ticket";
    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_QUEUE_TICKET_CALLED = "queue_ticket.called";
    private static final String OPERATION_CALL = "queue.call";
    private static final String OPERATION_CALL_FAILED = "queue.call.failed";
    private static final String TRANSITION_QUEUE_TICKET_CALL = "queue_ticket.call";

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
    private final QueueCallRule queueCallRule = new QueueCallRule();
    private final QueueCallHoldPolicy queueCallHoldPolicy = new QueueCallHoldPolicy();

    @Autowired
    public QueueCallApplicationService(
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

    public QueueCallApplicationService(
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
    public QueueCallResult callQueueTicket(CallQueueTicketCommand command) {
        QueueCallError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return QueueCallResult.failure(preValidationError);
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
            return QueueCallResult.failure(QueueCallError.PERSISTENCE_ERROR);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? QueueCallResult.retryLater(failure.error())
                : QueueCallResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, QueueCallError.PERSISTENCE_ERROR);
            appendFailureAudit(scope, command, started.idempotencyKey(), QueueCallError.PERSISTENCE_ERROR);
            return QueueCallResult.failure(QueueCallError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(CallQueueTicketCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.queueTicketId()),
            value(command.actorId()),
            normalize(command.actorType()),
            command.calledAt() == null ? "application_clock" : value(command.calledAt()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private QueueCallResult execute(CallQueueTicketCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(QueueCallError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(QueueCallError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), QueueCallError.STORE_ACCESS_DENIED);

        QueueTicket queueTicket = queueTicketRepository.findById(scope, new QueueTicketId(command.queueTicketId()))
            .orElseThrow(() -> new ApplicationFailure(QueueCallError.QUEUE_TICKET_NOT_FOUND));
        if (!scope.equals(queueTicket.scope())) {
            throw new ApplicationFailure(QueueCallError.STORE_SCOPE_MISMATCH);
        }

        if (queueTicket.status() == QueueTicketStatus.CALLED) {
            QueueCallError evidenceError = queueCallRule.validateAlreadyCalledEvidence(queueTicket);
            if (evidenceError != null) {
                throw new ApplicationFailure(evidenceError);
            }
            Optional<Reservation> reservation = loadReservationIfPresent(scope, queueTicket);
            return repeatCall(scope, command, started, queueTicket, reservation);
        }

        QueueCallError statusError = queueCallRule.validateFreshCall(queueTicket.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }

        Optional<Reservation> reservation = loadReservationIfPresent(scope, queueTicket);
        Instant calledAt = command.calledAt() == null ? Instant.now(clock) : command.calledAt();
        QueueCallHoldPolicy.HoldWindow holdWindow = resolveHoldWindow(scope, calledAt);

        if (!queueTicketStateMachine.canTransition(QueueTicketStatus.WAITING, QueueTicketStatus.CALLED)) {
            throw new ApplicationFailure(QueueCallError.ILLEGAL_STATE_TRANSITION);
        }

        QueueTicket savedTicket = saveCalled(scope, queueTicket.call(calledAt, holdWindow.holdUntilAt()));
        BusinessEvent event = appendBusinessEvent(scope, command, savedTicket, reservation, started.idempotencyKey(), holdWindow, QueueTicketStatus.WAITING.code(), false);
        StateTransitionLog transition = appendTransitionLog(scope, command, savedTicket, reservation, started.idempotencyKey(), holdWindow, QueueTicketStatus.WAITING.code());
        AuditLog auditLog = appendCompletedAudit(scope, command, savedTicket, reservation, started.idempotencyKey(), holdWindow, QueueTicketStatus.WAITING.code());
        IdempotencyRecord completed = completeIdempotency(scope, started, savedTicket, reservation, false);

        return QueueCallResult.success(
            savedTicket.id().value(),
            savedTicket.ticketNumber().value(),
            reservation.map(value -> value.id().value()).orElse(null),
            reservation.map(value -> value.reservationCode().value()).orElse(null),
            savedTicket.calledAt(),
            savedTicket.expiresAt(),
            completed.status().code(),
            List.of(EVENT_QUEUE_TICKET_CALLED),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private QueueCallResult repeatCall(
        StoreScope scope,
        CallQueueTicketCommand command,
        IdempotencyRecord started,
        QueueTicket queueTicket,
        Optional<Reservation> reservation
    ) {
        Instant calledAt = command.calledAt() == null ? Instant.now(clock) : command.calledAt();
        QueueCallHoldPolicy.HoldWindow holdWindow = resolveHoldWindow(scope, calledAt);
        QueueTicket savedTicket = saveCalled(scope, queueTicket.call(calledAt, holdWindow.holdUntilAt()));
        BusinessEvent event = appendBusinessEvent(scope, command, savedTicket, reservation, started.idempotencyKey(), holdWindow, QueueTicketStatus.CALLED.code(), false);
        StateTransitionLog transition = appendTransitionLog(scope, command, savedTicket, reservation, started.idempotencyKey(), holdWindow, QueueTicketStatus.CALLED.code());
        AuditLog auditLog = appendCompletedAudit(scope, command, savedTicket, reservation, started.idempotencyKey(), holdWindow, QueueTicketStatus.CALLED.code());
        IdempotencyRecord completed = completeIdempotency(scope, started, savedTicket, reservation, false);

        return QueueCallResult.success(
            savedTicket.id().value(),
            savedTicket.ticketNumber().value(),
            reservation.map(value -> value.id().value()).orElse(null),
            reservation.map(value -> value.reservationCode().value()).orElse(null),
            savedTicket.calledAt(),
            savedTicket.expiresAt(),
            completed.status().code(),
            List.of(EVENT_QUEUE_TICKET_CALLED),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private Optional<Reservation> loadReservationIfPresent(StoreScope scope, QueueTicket queueTicket) {
        if (queueTicket.reservationId() == null) {
            return Optional.empty();
        }
        Reservation reservation = reservationRepository.findById(scope, new ReservationId(queueTicket.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(QueueCallError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(QueueCallError.STORE_SCOPE_MISMATCH);
        }
        if (reservation.status() != ReservationStatus.ARRIVED) {
            throw new ApplicationFailure(QueueCallError.RESERVATION_STATUS_NOT_ARRIVED);
        }
        return Optional.of(reservation);
    }

    private QueueCallHoldPolicy.HoldWindow resolveHoldWindow(StoreScope scope, Instant calledAt) {
        try {
            Optional<StorePolicy> policy = storeRepository.findCurrentPolicy(scope, OffsetDateTime.ofInstant(calledAt, ZoneOffset.UTC));
            return queueCallHoldPolicy.resolve(policy, calledAt);
        } catch (IllegalArgumentException exception) {
            throw new ApplicationFailure(QueueCallError.QUEUE_CALL_HOLD_POLICY_INVALID, exception);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCallError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueTicket saveCalled(StoreScope scope, QueueTicket queueTicket) {
        try {
            return queueTicketRepository.save(scope, queueTicket);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCallError.PERSISTENCE_ERROR, exception);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        CallQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        IdempotencyKey idempotencyKey,
        QueueCallHoldPolicy.HoldWindow holdWindow,
        String beforeStatus,
        boolean alreadyCalled
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_QUEUE_TICKET_CALLED,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(queueTicket, reservation, command, idempotencyKey, holdWindow, beforeStatus, alreadyCalled)
        );
        require(
            businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
            QueueCallError.BUSINESS_EVENT_WRITE_FAILED
        );
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCallError.BUSINESS_EVENT_WRITE_FAILED, exception);
        }
    }

    private StateTransitionLog appendTransitionLog(
        StoreScope scope,
        CallQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        IdempotencyKey idempotencyKey,
        QueueCallHoldPolicy.HoldWindow holdWindow,
        String beforeStatus
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            beforeStatus,
            QueueTicketStatus.CALLED.code(),
            TRANSITION_QUEUE_TICKET_CALL,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(queueTicket, reservation, command, idempotencyKey, holdWindow, beforeStatus, false)
        );
        require(
            stateTransitionRule.evaluate(
                transition.targetType(),
                transition.targetId(),
                transition.toStatus(),
                transition.transitionCode(),
                transition.actorType()
            ),
            QueueCallError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return stateTransitionLogRepository.append(scope, transition);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCallError.STATE_TRANSITION_WRITE_FAILED, exception);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        CallQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        IdempotencyKey idempotencyKey,
        QueueCallHoldPolicy.HoldWindow holdWindow,
        String beforeStatus
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_CALL,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(queueTicket, reservation, command, idempotencyKey, holdWindow, beforeStatus, false)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), QueueCallError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCallError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        CallQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        QueueCallError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_CALL_FAILED,
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
        boolean alreadyCalled
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
            snapshot(queueTicket, reservation, alreadyCalled)
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, TARGET_QUEUE_TICKET);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueCallError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueCallResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            QueueCallError error = QueueCallError.fromCode(decision.violationCode());
            if (error == QueueCallError.IDEMPOTENCY_IN_PROGRESS) {
                return QueueCallResult.retryLater(error);
            }
            return QueueCallResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return QueueCallResult.failure(QueueCallError.IDEMPOTENCY_CONFLICT);
            }
        }
        return QueueCallResult.failure(QueueCallError.IDEMPOTENCY_CONFLICT);
    }

    private QueueCallResult replay(String snapshot) {
        return QueueCallResult.replay(
            UUID.fromString(extract(snapshot, "queueTicketId")),
            Integer.parseInt(extractNumber(snapshot, "queueTicketNumber")),
            extract(snapshot, "queueTicketStatus"),
            extractNullableUuid(snapshot, "reservationId"),
            extractNullableString(snapshot, "reservationCode"),
            extractNullableString(snapshot, "reservationStatus"),
            Instant.parse(extract(snapshot, "calledAt")),
            Instant.parse(extract(snapshot, "holdUntilAt")),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyCalled"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, QueueCallError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static QueueCallError validateCommand(CallQueueTicketCommand command) {
        if (command == null) {
            return QueueCallError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return QueueCallError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.queueTicketId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return QueueCallError.INVALID_COMMAND;
        }
        return null;
    }

    private static void require(RuleDecision decision, QueueCallError fallback) {
        if (!decision.accepted()) {
            QueueCallError error = QueueCallError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == QueueCallError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(QueueTicket queueTicket, Optional<Reservation> reservation, boolean alreadyCalled) {
        String reservationId = reservation.map(value -> "\"" + value.id().value() + "\"").orElse("null");
        String reservationCode = reservation.map(value -> "\"" + escape(value.reservationCode().value()) + "\"").orElse("null");
        String reservationStatus = reservation.map(value -> "\"" + value.status().code() + "\"").orElse("null");
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"%s","reservationId":%s,"reservationCode":%s,"reservationStatus":%s,"calledAt":"%s","holdUntilAt":"%s","alreadyCalled":%s}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.status().code(),
            reservationId,
            reservationCode,
            reservationStatus,
            queueTicket.calledAt(),
            queueTicket.expiresAt(),
            alreadyCalled
        ).trim();
    }

    private static String metadata(
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        CallQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        QueueCallHoldPolicy.HoldWindow holdWindow,
        String beforeStatus,
        boolean alreadyCalled
    ) {
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"beforeQueueTicketStatus":"%s","afterQueueTicketStatus":"called","reservationId":%s,"reservationCode":%s,"reservationStatus":%s,"queueGroupId":"%s","businessDate":"%s","partySize":%d,"queuePosition":%s,"calledAt":"%s","holdUntilAt":"%s","queueCallHoldMinutes":%d,"holdPolicySource":"%s","alreadyCalled":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            beforeStatus,
            jsonNullable(reservation.map(value -> value.id().value()).orElse(null)),
            jsonNullable(reservation.map(value -> value.reservationCode().value()).orElse(null)),
            jsonNullable(reservation.map(value -> value.status().code()).orElse(null)),
            queueTicket.queueGroupId(),
            queueTicket.businessDate().value(),
            queueTicket.partySize().value(),
            queueTicket.queuePosition() == null ? "null" : queueTicket.queuePosition(),
            queueTicket.calledAt(),
            queueTicket.expiresAt(),
            holdWindow.minutes(),
            holdWindow.source(),
            alreadyCalled,
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

    private static String source(CallQueueTicketCommand command) {
        return hasText(command.actorType()) ? command.actorType().trim() : "staff";
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
    }

    private static String jsonNullable(UUID value) {
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
        private final QueueCallError error;
        private final boolean retryLater;

        private ApplicationFailure(QueueCallError error) {
            this(error, false, null);
        }

        private ApplicationFailure(QueueCallError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(QueueCallError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(QueueCallError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private QueueCallError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
