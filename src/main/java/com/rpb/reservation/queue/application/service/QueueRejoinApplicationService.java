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
import com.rpb.reservation.queue.application.QueueRejoinError;
import com.rpb.reservation.queue.application.QueueRejoinResult;
import com.rpb.reservation.queue.application.command.RejoinQueueTicketCommand;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.rule.QueueRejoinEvidenceRule;
import com.rpb.reservation.queue.application.rule.QueueRejoinRule;
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
public class QueueRejoinApplicationService {

    private static final String ACTION = "rejoin_queue_ticket";
    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_QUEUE_TICKET_REJOINED = "queue_ticket.rejoined";
    private static final String OPERATION_REJOIN = "queue.rejoin";
    private static final String OPERATION_REJOIN_FAILED = "queue.rejoin.failed";
    private static final String TRANSITION_QUEUE_TICKET_REJOIN = "queue_ticket.rejoin";

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
    private final QueueRejoinRule queueRejoinRule = new QueueRejoinRule();
    private final QueueRejoinEvidenceRule queueRejoinEvidenceRule = new QueueRejoinEvidenceRule();

    @Autowired
    public QueueRejoinApplicationService(
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

    public QueueRejoinApplicationService(
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
    public QueueRejoinResult rejoinQueueTicket(RejoinQueueTicketCommand command) {
        QueueRejoinError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return QueueRejoinResult.failure(preValidationError);
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
            return QueueRejoinResult.failure(QueueRejoinError.PERSISTENCE_ERROR);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? QueueRejoinResult.retryLater(failure.error())
                : QueueRejoinResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, QueueRejoinError.PERSISTENCE_ERROR);
            appendFailureAudit(scope, command, started.idempotencyKey(), QueueRejoinError.PERSISTENCE_ERROR);
            return QueueRejoinResult.failure(QueueRejoinError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(RejoinQueueTicketCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.queueTicketId()),
            value(command.actorId()),
            normalize(command.actorType()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private QueueRejoinResult execute(RejoinQueueTicketCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(QueueRejoinError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(QueueRejoinError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), QueueRejoinError.STORE_ACCESS_DENIED);

        QueueTicket queueTicket = queueTicketRepository.findById(scope, new QueueTicketId(command.queueTicketId()))
            .orElseThrow(() -> new ApplicationFailure(QueueRejoinError.QUEUE_TICKET_NOT_FOUND));
        if (!scope.equals(queueTicket.scope())) {
            throw new ApplicationFailure(QueueRejoinError.STORE_SCOPE_MISMATCH);
        }

        Reservation reservation = loadReservation(scope, queueTicket);

        if (queueTicket.status() == QueueTicketStatus.WAITING && queueTicket.rejoinedAt() != null) {
            return alreadyRejoined(scope, started, queueTicket, reservation);
        }

        QueueRejoinError statusError = queueRejoinRule.validateFreshRejoin(queueTicket.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }
        QueueRejoinError evidenceError = queueRejoinEvidenceRule.validateSkippedSourceEvidence(
            queueTicket,
            businessEventRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value()),
            stateTransitionLogRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value()),
            auditLogRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value())
        );
        if (evidenceError != null) {
            throw new ApplicationFailure(evidenceError);
        }
        if (!queueTicketStateMachine.canTransition(QueueTicketStatus.SKIPPED, QueueTicketStatus.WAITING)) {
            throw new ApplicationFailure(QueueRejoinError.ILLEGAL_STATE_TRANSITION);
        }

        Instant rejoinedAt = Instant.now(clock);
        int queuePosition = nextTailPosition(scope, queueTicket);
        QueueTicket savedTicket = saveRejoined(scope, queueTicket.rejoin(rejoinedAt, queuePosition));
        BusinessEvent event = appendBusinessEvent(scope, command, savedTicket, reservation, started.idempotencyKey(), false);
        StateTransitionLog transition = appendTransitionLog(scope, command, savedTicket, reservation, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, savedTicket, reservation, started.idempotencyKey());
        IdempotencyRecord completed = completeIdempotency(scope, started, savedTicket, reservation, false);

        return QueueRejoinResult.success(
            savedTicket.id().value(),
            savedTicket.ticketNumber().value(),
            savedTicket.queuePosition(),
            reservation.id().value(),
            reservation.reservationCode().value(),
            savedTicket.rejoinedAt(),
            completed.status().code(),
            List.of(EVENT_QUEUE_TICKET_REJOINED),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private QueueRejoinResult alreadyRejoined(
        StoreScope scope,
        IdempotencyRecord started,
        QueueTicket queueTicket,
        Reservation reservation
    ) {
        QueueRejoinError evidenceError = queueRejoinEvidenceRule.validateAlreadyRejoinedEvidence(
            queueTicket,
            businessEventRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value()),
            stateTransitionLogRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value()),
            auditLogRepository.findByTarget(scope, TARGET_QUEUE_TICKET, queueTicket.id().value())
        );
        if (evidenceError != null) {
            throw new ApplicationFailure(evidenceError);
        }
        IdempotencyRecord completed = completeIdempotency(scope, started, queueTicket, reservation, true);
        return QueueRejoinResult.alreadyRejoined(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.queuePosition(),
            reservation.id().value(),
            reservation.reservationCode().value(),
            queueTicket.rejoinedAt(),
            completed.status().code()
        );
    }

    private Reservation loadReservation(StoreScope scope, QueueTicket queueTicket) {
        if (queueTicket.reservationId() == null) {
            throw new ApplicationFailure(QueueRejoinError.RESERVATION_NOT_FOUND);
        }
        Reservation reservation = reservationRepository.findById(scope, new ReservationId(queueTicket.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(QueueRejoinError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(QueueRejoinError.STORE_SCOPE_MISMATCH);
        }
        if (reservation.status() != ReservationStatus.ARRIVED) {
            throw new ApplicationFailure(QueueRejoinError.RESERVATION_STATUS_NOT_ARRIVED);
        }
        return reservation;
    }

    private int nextTailPosition(StoreScope scope, QueueTicket queueTicket) {
        return queueTicketRepository.findActiveQueue(scope, queueTicket.queueGroupId(), queueTicket.businessDate()).stream()
            .filter(ticket -> queueTicket.queueGroupId().equals(ticket.queueGroupId()))
            .filter(ticket -> queueTicket.businessDate().equals(ticket.businessDate()))
            .map(QueueTicket::queuePosition)
            .filter(position -> position != null && position > 0)
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0) + 1;
    }

    private QueueTicket saveRejoined(StoreScope scope, QueueTicket queueTicket) {
        try {
            return queueTicketRepository.save(scope, queueTicket);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueRejoinError.PERSISTENCE_ERROR, exception);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        RejoinQueueTicketCommand command,
        QueueTicket queueTicket,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        boolean alreadyRejoined
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_QUEUE_TICKET_REJOINED,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(queueTicket, reservation, command, idempotencyKey, alreadyRejoined)
        );
        require(
            businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
            QueueRejoinError.BUSINESS_EVENT_WRITE_FAILED
        );
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueRejoinError.BUSINESS_EVENT_WRITE_FAILED, exception);
        }
    }

    private StateTransitionLog appendTransitionLog(
        StoreScope scope,
        RejoinQueueTicketCommand command,
        QueueTicket queueTicket,
        Reservation reservation,
        IdempotencyKey idempotencyKey
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            QueueTicketStatus.SKIPPED.code(),
            QueueTicketStatus.WAITING.code(),
            TRANSITION_QUEUE_TICKET_REJOIN,
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
            QueueRejoinError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return stateTransitionLogRepository.append(scope, transition);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueRejoinError.STATE_TRANSITION_WRITE_FAILED, exception);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        RejoinQueueTicketCommand command,
        QueueTicket queueTicket,
        Reservation reservation,
        IdempotencyKey idempotencyKey
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_REJOIN,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(queueTicket, reservation, command, idempotencyKey, false)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), QueueRejoinError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueRejoinError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        RejoinQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        QueueRejoinError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_REJOIN_FAILED,
                    TARGET_QUEUE_TICKET,
                    command.queueTicketId(),
                    source(command),
                    command.actorType(),
                    command.actorId(),
                    """
                        {"failureReason":"%s","queueTicketId":"%s","note":%s,"idempotencyKey":"%s"}
                        """.formatted(
                        error.code(),
                        command.queueTicketId(),
                        jsonNullable(command.note()),
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
        Reservation reservation,
        boolean alreadyRejoined
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
            snapshot(queueTicket, reservation, alreadyRejoined)
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, TARGET_QUEUE_TICKET);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(QueueRejoinError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueRejoinResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            QueueRejoinError error = QueueRejoinError.fromCode(decision.violationCode());
            if (error == QueueRejoinError.IDEMPOTENCY_IN_PROGRESS) {
                return QueueRejoinResult.retryLater(error);
            }
            return QueueRejoinResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return QueueRejoinResult.failure(QueueRejoinError.IDEMPOTENCY_CONFLICT);
            }
        }
        return QueueRejoinResult.failure(QueueRejoinError.IDEMPOTENCY_CONFLICT);
    }

    private QueueRejoinResult replay(String snapshot) {
        return QueueRejoinResult.replay(
            UUID.fromString(extract(snapshot, "queueTicketId")),
            Integer.parseInt(extractNumber(snapshot, "queueTicketNumber")),
            extract(snapshot, "queueTicketStatus"),
            extractOptionalNumber(snapshot, "queuePosition"),
            UUID.fromString(extract(snapshot, "reservationId")),
            extract(snapshot, "reservationCode"),
            extract(snapshot, "reservationStatus"),
            Instant.parse(extract(snapshot, "rejoinedAt")),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyRejoined"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, QueueRejoinError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static QueueRejoinError validateCommand(RejoinQueueTicketCommand command) {
        if (command == null) {
            return QueueRejoinError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return QueueRejoinError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.queueTicketId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return QueueRejoinError.INVALID_COMMAND;
        }
        return null;
    }

    private static void require(RuleDecision decision, QueueRejoinError fallback) {
        if (!decision.accepted()) {
            QueueRejoinError error = QueueRejoinError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == QueueRejoinError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(QueueTicket queueTicket, Reservation reservation, boolean alreadyRejoined) {
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"waiting","queuePosition":%s,"reservationId":"%s","reservationCode":"%s","reservationStatus":"arrived","rejoinedAt":"%s","alreadyRejoined":%s}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.queuePosition() == null ? "null" : queueTicket.queuePosition(),
            reservation.id().value(),
            escape(reservation.reservationCode().value()),
            queueTicket.rejoinedAt(),
            alreadyRejoined
        ).trim();
    }

    private static String metadata(
        QueueTicket queueTicket,
        Reservation reservation,
        RejoinQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        boolean alreadyRejoined
    ) {
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"beforeQueueTicketStatus":"skipped","afterQueueTicketStatus":"waiting","reservationId":"%s","reservationCode":"%s","reservationStatus":"arrived","queueGroupId":"%s","businessDate":"%s","partySize":%d,"queuePosition":%s,"skippedAt":%s,"rejoinedAt":"%s","alreadyRejoined":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            reservation.id().value(),
            escape(reservation.reservationCode().value()),
            queueTicket.queueGroupId(),
            queueTicket.businessDate().value(),
            queueTicket.partySize().value(),
            queueTicket.queuePosition() == null ? "null" : queueTicket.queuePosition(),
            jsonNullable(queueTicket.skippedAt()),
            queueTicket.rejoinedAt(),
            alreadyRejoined,
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

    private static String extractNumber(String json, String key) {
        Pattern number = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = number.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static Integer extractOptionalNumber(String json, String key) {
        Pattern number = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+|null)");
        Matcher matcher = number.matcher(json);
        if (matcher.find()) {
            return "null".equals(matcher.group(1)) ? null : Integer.parseInt(matcher.group(1));
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

    private static String source(RejoinQueueTicketCommand command) {
        return hasText(command.actorType()) ? command.actorType().trim() : "staff";
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
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
        private final QueueRejoinError error;
        private final boolean retryLater;

        private ApplicationFailure(QueueRejoinError error) {
            this(error, false, null);
        }

        private ApplicationFailure(QueueRejoinError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(QueueRejoinError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(QueueRejoinError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private QueueRejoinError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
