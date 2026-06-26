package com.rpb.reservation.reservation.application.service;

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
import com.rpb.reservation.reservation.application.ReservationCancelError;
import com.rpb.reservation.reservation.application.ReservationCancelResult;
import com.rpb.reservation.reservation.application.command.CancelReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.rule.ReservationCancellationRule;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.state.ReservationStateMachine;
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
public class ReservationCancelApplicationService {

    private static final String ACTION = "cancel_reservation";
    private static final String TARGET_RESERVATION = "reservation";
    private static final String EVENT_RESERVATION_CANCELLED = "reservation.cancelled";
    private static final String OPERATION_CANCEL = "reservation.cancel";
    private static final String OPERATION_CANCEL_FAILED = "reservation.cancel.failed";

    private final StoreRepositoryPort storeRepository;
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
    private final ReservationStateMachine reservationStateMachine = new ReservationStateMachine();
    private final ReservationCancellationRule cancellationRule = new ReservationCancellationRule();

    @Autowired
    public ReservationCancelApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this(
            storeRepository,
            reservationRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            Clock.systemUTC()
        );
    }

    public ReservationCancelApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.reservationRepository = reservationRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationCancelResult cancelReservation(CancelReservationCommand command) {
        ReservationCancelError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return ReservationCancelResult.failure(preValidationError);
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
            started = idempotencyRepository.start(
                scope,
                source,
                ACTION,
                idempotencyKey,
                requestHash,
                OffsetDateTime.now(clock).plusMinutes(30)
            );
        } catch (RuntimeException exception) {
            return ReservationCancelResult.failure(ReservationCancelError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? ReservationCancelResult.retryLater(failure.error())
                : ReservationCancelResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationCancelError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, started.idempotencyKey(), ReservationCancelError.REPOSITORY_SAVE_FAILED);
            return ReservationCancelResult.failure(ReservationCancelError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(CancelReservationCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.reservationId()),
            value(command.actorId()),
            normalize(command.actorType()),
            command.cancelledAt() == null ? "application_clock" : value(command.cancelledAt()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private ReservationCancelResult execute(CancelReservationCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationCancelError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(ReservationCancelError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), ReservationCancelError.STORE_ACCESS_DENIED);

        Reservation reservation = reservationRepository.findById(scope, new ReservationId(command.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationCancelError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(ReservationCancelError.STORE_SCOPE_MISMATCH);
        }

        if (reservation.status() == ReservationStatus.CANCELLED) {
            Instant cancellationEvidence = existingCancelledAt(scope, reservation)
                .orElseGet(() -> reservation.updatedAt() == null ? Instant.now(clock) : reservation.updatedAt());
            IdempotencyRecord completed = completeIdempotency(scope, started, reservation, cancellationEvidence, true);
            return ReservationCancelResult.alreadyCancelled(
                reservation.id().value(),
                reservation.reservationCode().value(),
                cancellationEvidence,
                reservation.cancellationReasonCode(),
                completed.status().code()
            );
        }

        ReservationCancelError statusError = cancellationRule.validateFreshCancellation(reservation.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }

        if (!reservationStateMachine.canTransition(reservation.status(), ReservationStatus.CANCELLED)) {
            throw new ApplicationFailure(ReservationCancelError.ILLEGAL_STATE_TRANSITION);
        }

        Instant cancelledAt = command.cancelledAt() == null ? Instant.now(clock) : command.cancelledAt();
        Reservation cancelled = cancel(reservation, cancelledAt, command.reasonCode());
        Reservation saved = saveCancelled(scope, cancelled);
        BusinessEvent event = appendBusinessEvent(scope, command, reservation.status(), saved, started.idempotencyKey(), cancelledAt);
        StateTransitionLog transition = appendTransitionLog(scope, command, reservation.status(), saved, started.idempotencyKey(), cancelledAt);
        AuditLog auditLog = appendCompletedAudit(scope, command, reservation.status(), saved, started.idempotencyKey(), cancelledAt);
        IdempotencyRecord completed = completeIdempotency(scope, started, saved, cancelledAt, false);

        return ReservationCancelResult.success(
            saved.id().value(),
            saved.reservationCode().value(),
            saved.status().code(),
            cancelledAt,
            saved.cancellationReasonCode(),
            completed.status().code(),
            List.of(EVENT_RESERVATION_CANCELLED),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private Reservation saveCancelled(StoreScope scope, Reservation reservation) {
        try {
            return reservationRepository.save(scope, reservation);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCancelError.REPOSITORY_SAVE_FAILED);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        CancelReservationCommand command,
        ReservationStatus fromStatus,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant cancelledAt
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_RESERVATION_CANCELLED,
            TARGET_RESERVATION,
            reservation.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, fromStatus, idempotencyKey, cancelledAt, false)
        );
        require(
            businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
            ReservationCancelError.BUSINESS_EVENT_WRITE_FAILED
        );
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCancelError.BUSINESS_EVENT_WRITE_FAILED);
        }
    }

    private StateTransitionLog appendTransitionLog(
        StoreScope scope,
        CancelReservationCommand command,
        ReservationStatus fromStatus,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant cancelledAt
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_RESERVATION,
            reservation.id().value(),
            fromStatus.code(),
            ReservationStatus.CANCELLED.code(),
            OPERATION_CANCEL,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, fromStatus, idempotencyKey, cancelledAt, false)
        );
        require(
            stateTransitionRule.evaluate(
                transition.targetType(),
                transition.targetId(),
                transition.toStatus(),
                transition.transitionCode(),
                transition.actorType()
            ),
            ReservationCancelError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return stateTransitionLogRepository.append(scope, transition);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCancelError.STATE_TRANSITION_WRITE_FAILED);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        CancelReservationCommand command,
        ReservationStatus fromStatus,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant cancelledAt
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_CANCEL,
            TARGET_RESERVATION,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, command, fromStatus, idempotencyKey, cancelledAt, false)
        );
        require(
            auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()),
            ReservationCancelError.AUDIT_WRITE_FAILED
        );
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCancelError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        CancelReservationCommand command,
        IdempotencyKey idempotencyKey,
        ReservationCancelError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_CANCEL_FAILED,
                    TARGET_RESERVATION,
                    command == null ? null : command.reservationId(),
                    source(command),
                    command == null ? "system" : command.actorType(),
                    command == null ? null : command.actorId(),
                    """
                        {"failureReason":"%s","reservationId":%s,"reasonCode":%s,"idempotencyKey":"%s"}
                        """.formatted(
                        error.code(),
                        command == null || command.reservationId() == null ? "null" : "\"" + command.reservationId() + "\"",
                        command == null ? "null" : jsonNullable(command.reasonCode()),
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
        Reservation reservation,
        Instant cancelledAt,
        boolean alreadyCancelled
    ) {
        String snapshot = snapshot(reservation, cancelledAt, alreadyCancelled);
        IdempotencyRecord completionPayload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            TARGET_RESERVATION,
            reservation.id().value(),
            snapshot
        );
        return idempotencyRepository.complete(scope, completionPayload, TARGET_RESERVATION);
    }

    private ReservationCancelResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationCancelError error = ReservationCancelError.fromCode(decision.violationCode());
            if (error == ReservationCancelError.COMMAND_IN_PROGRESS) {
                return ReservationCancelResult.retryLater(error);
            }
            return ReservationCancelResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return ReservationCancelResult.failure(ReservationCancelError.IDEMPOTENCY_CONFLICT);
            }
        }
        return ReservationCancelResult.failure(ReservationCancelError.IDEMPOTENCY_CONFLICT);
    }

    private ReservationCancelResult replay(String snapshot) {
        return ReservationCancelResult.replay(
            UUID.fromString(extract(snapshot, "reservationId")),
            extract(snapshot, "reservationCode"),
            extract(snapshot, "status"),
            Instant.parse(extract(snapshot, "cancelledAt")),
            extractNullable(snapshot, "cancellationReasonCode"),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyCancelled"))
        );
    }

    private Optional<Instant> existingCancelledAt(StoreScope scope, Reservation reservation) {
        return stateTransitionLogRepository.findLatest(scope, TARGET_RESERVATION, reservation.id().value())
            .filter(transition -> ReservationStatus.CANCELLED.code().equals(transition.toStatus()))
            .flatMap(transition -> extractInstant(transition.metadata(), "cancelledAt"));
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationCancelError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static ReservationCancelError validateCommand(CancelReservationCommand command) {
        if (command == null) {
            return ReservationCancelError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationCancelError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationCancelError.INVALID_COMMAND;
        }
        return null;
    }

    private static Reservation cancel(Reservation reservation, Instant cancelledAt, String reasonCode) {
        return new Reservation(
            reservation.id(),
            reservation.scope(),
            reservation.customerId(),
            reservation.reservationCode(),
            reservation.partySize(),
            reservation.businessDate(),
            reservation.reservedStartAt(),
            reservation.reservedEndAt(),
            reservation.holdUntilAt(),
            ReservationStatus.CANCELLED,
            reservation.sourceChannel(),
            trimToNull(reasonCode),
            reservation.noShowReasonCode(),
            reservation.note(),
            reservation.createdAt(),
            cancelledAt,
            reservation.deletedAt()
        );
    }

    private static void require(RuleDecision decision, ReservationCancelError fallback) {
        if (!decision.accepted()) {
            ReservationCancelError error = ReservationCancelError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == ReservationCancelError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(Reservation reservation, Instant cancelledAt, boolean alreadyCancelled) {
        return """
            {"reservationId":"%s","reservationCode":"%s","status":"cancelled","cancelledAt":"%s","cancellationReasonCode":%s,"alreadyCancelled":%s}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            cancelledAt,
            jsonNullable(reservation.cancellationReasonCode()),
            alreadyCancelled
        ).trim();
    }

    private static String metadata(
        Reservation reservation,
        CancelReservationCommand command,
        ReservationStatus fromStatus,
        IdempotencyKey idempotencyKey,
        Instant cancelledAt,
        boolean alreadyCancelled
    ) {
        return """
            {"reservationId":"%s","reservationCode":"%s","beforeStatus":"%s","afterStatus":"cancelled","cancelledAt":"%s","alreadyCancelled":%s,"source":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            fromStatus.code(),
            cancelledAt,
            alreadyCancelled,
            jsonNullable(source(command)),
            jsonNullable(command.reasonCode()),
            jsonNullable(command.note()),
            escape(idempotencyKey.value())
        ).trim();
    }

    private static Optional<Instant> extractInstant(String json, String key) {
        try {
            return Optional.of(Instant.parse(extract(json, key)));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static String extract(String json, String key) {
        if (json == null) {
            throw new IllegalArgumentException("snapshot_missing");
        }
        Pattern quoted = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher quotedMatcher = quoted.matcher(json);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String extractNullable(String json, String key) {
        if (json == null) {
            throw new IllegalArgumentException("snapshot_missing");
        }
        Pattern quoted = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher quotedMatcher = quoted.matcher(json);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }
        Pattern nullValue = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*null");
        if (nullValue.matcher(json).find()) {
            return null;
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String extractBoolean(String json, String key) {
        if (json == null) {
            throw new IllegalArgumentException("snapshot_missing");
        }
        Pattern bool = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = bool.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String source(CancelReservationCommand command) {
        return OperationSource.fromActorType(command == null ? null : command.actorType());
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
    }

    private static String trimToNull(String value) {
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
        private final ReservationCancelError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationCancelError error) {
            this(error, false);
        }

        private ApplicationFailure(ReservationCancelError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationCancelError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
