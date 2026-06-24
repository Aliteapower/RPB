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
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.ReservationNoShowError;
import com.rpb.reservation.reservation.application.ReservationNoShowResult;
import com.rpb.reservation.reservation.application.command.MarkReservationNoShowCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
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
public class ReservationNoShowApplicationService {
    private static final String ACTION = "mark_reservation_no_show";
    private static final String TARGET_RESERVATION = "reservation";
    private static final String EVENT_RESERVATION_NO_SHOW = "reservation.no_show";
    private static final String OPERATION_NO_SHOW = "reservation.no_show";
    private static final String OPERATION_NO_SHOW_FAILED = "reservation.no_show.failed";

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

    @Autowired
    public ReservationNoShowApplicationService(
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

    public ReservationNoShowApplicationService(
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
    public ReservationNoShowResult markNoShow(MarkReservationNoShowCommand command) {
        ReservationNoShowError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return ReservationNoShowResult.failure(preValidationError);
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
            return ReservationNoShowResult.failure(ReservationNoShowError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? ReservationNoShowResult.retryLater(failure.error())
                : ReservationNoShowResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationNoShowError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, started.idempotencyKey(), ReservationNoShowError.REPOSITORY_SAVE_FAILED);
            return ReservationNoShowResult.failure(ReservationNoShowError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(MarkReservationNoShowCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.reservationId()),
            value(command.actorId()),
            normalize(command.actorType()),
            command.noShowAt() == null ? "application_clock" : value(command.noShowAt()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private ReservationNoShowResult execute(
        MarkReservationNoShowCommand command,
        StoreScope scope,
        IdempotencyRecord started
    ) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationNoShowError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(ReservationNoShowError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), ReservationNoShowError.STORE_ACCESS_DENIED);

        Reservation reservation = reservationRepository.findById(scope, new ReservationId(command.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationNoShowError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(ReservationNoShowError.STORE_SCOPE_MISMATCH);
        }

        if (reservation.status() == ReservationStatus.NO_SHOW) {
            Instant evidence = existingNoShowAt(scope, reservation)
                .orElseGet(() -> reservation.updatedAt() == null ? Instant.now(clock) : reservation.updatedAt());
            IdempotencyRecord completed = completeIdempotency(scope, started, reservation, evidence, true);
            return ReservationNoShowResult.alreadyNoShow(
                reservation.id().value(),
                reservation.reservationCode().value(),
                evidence,
                reservation.noShowReasonCode(),
                completed.status().code()
            );
        }

        ReservationNoShowError statusError = validateFreshNoShow(reservation.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }
        if (!reservationStateMachine.canTransition(reservation.status(), ReservationStatus.NO_SHOW)) {
            throw new ApplicationFailure(ReservationNoShowError.ILLEGAL_STATE_TRANSITION);
        }

        Instant noShowAt = command.noShowAt() == null ? Instant.now(clock) : command.noShowAt();
        Reservation noShow = markNoShow(reservation, noShowAt, command.reasonCode());
        Reservation saved = saveReservation(scope, noShow);
        BusinessEvent event = appendBusinessEvent(scope, command, reservation.status(), saved, started.idempotencyKey(), noShowAt);
        StateTransitionLog transition = appendTransitionLog(scope, command, reservation.status(), saved, started.idempotencyKey(), noShowAt);
        AuditLog auditLog = appendCompletedAudit(scope, command, reservation.status(), saved, started.idempotencyKey(), noShowAt);
        IdempotencyRecord completed = completeIdempotency(scope, started, saved, noShowAt, false);

        return ReservationNoShowResult.success(
            saved.id().value(),
            saved.reservationCode().value(),
            saved.status().code(),
            noShowAt,
            saved.noShowReasonCode(),
            completed.status().code(),
            List.of(EVENT_RESERVATION_NO_SHOW),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private Reservation saveReservation(StoreScope scope, Reservation reservation) {
        try {
            return reservationRepository.save(scope, reservation);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationNoShowError.REPOSITORY_SAVE_FAILED);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        MarkReservationNoShowCommand command,
        ReservationStatus fromStatus,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant noShowAt
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_RESERVATION_NO_SHOW,
            TARGET_RESERVATION,
            reservation.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, fromStatus, idempotencyKey, noShowAt, false)
        );
        require(
            businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
            ReservationNoShowError.BUSINESS_EVENT_WRITE_FAILED
        );
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationNoShowError.BUSINESS_EVENT_WRITE_FAILED);
        }
    }

    private StateTransitionLog appendTransitionLog(
        StoreScope scope,
        MarkReservationNoShowCommand command,
        ReservationStatus fromStatus,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant noShowAt
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_RESERVATION,
            reservation.id().value(),
            fromStatus.code(),
            ReservationStatus.NO_SHOW.code(),
            OPERATION_NO_SHOW,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, fromStatus, idempotencyKey, noShowAt, false)
        );
        require(
            stateTransitionRule.evaluate(
                transition.targetType(),
                transition.targetId(),
                transition.toStatus(),
                transition.transitionCode(),
                transition.actorType()
            ),
            ReservationNoShowError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return stateTransitionLogRepository.append(scope, transition);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationNoShowError.STATE_TRANSITION_WRITE_FAILED);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        MarkReservationNoShowCommand command,
        ReservationStatus fromStatus,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant noShowAt
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_NO_SHOW,
            TARGET_RESERVATION,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, command, fromStatus, idempotencyKey, noShowAt, false)
        );
        require(
            auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()),
            ReservationNoShowError.AUDIT_WRITE_FAILED
        );
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationNoShowError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        MarkReservationNoShowCommand command,
        IdempotencyKey idempotencyKey,
        ReservationNoShowError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_NO_SHOW_FAILED,
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
        Instant noShowAt,
        boolean alreadyNoShow
    ) {
        String snapshot = snapshot(reservation, noShowAt, alreadyNoShow);
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

    private ReservationNoShowResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationNoShowError error = ReservationNoShowError.fromCode(decision.violationCode());
            if (error == ReservationNoShowError.COMMAND_IN_PROGRESS) {
                return ReservationNoShowResult.retryLater(error);
            }
            return ReservationNoShowResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return ReservationNoShowResult.failure(ReservationNoShowError.IDEMPOTENCY_CONFLICT);
            }
        }
        return ReservationNoShowResult.failure(ReservationNoShowError.IDEMPOTENCY_CONFLICT);
    }

    private ReservationNoShowResult replay(String snapshot) {
        return ReservationNoShowResult.replay(
            UUID.fromString(extract(snapshot, "reservationId")),
            extract(snapshot, "reservationCode"),
            extract(snapshot, "status"),
            Instant.parse(extract(snapshot, "noShowAt")),
            extractNullable(snapshot, "noShowReasonCode"),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyNoShow"))
        );
    }

    private Optional<Instant> existingNoShowAt(StoreScope scope, Reservation reservation) {
        return stateTransitionLogRepository.findLatest(scope, TARGET_RESERVATION, reservation.id().value())
            .filter(transition -> ReservationStatus.NO_SHOW.code().equals(transition.toStatus()))
            .flatMap(transition -> extractInstant(transition.metadata(), "noShowAt"));
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationNoShowError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static ReservationNoShowError validateCommand(MarkReservationNoShowCommand command) {
        if (command == null) {
            return ReservationNoShowError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationNoShowError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationNoShowError.INVALID_COMMAND;
        }
        return null;
    }

    private static ReservationNoShowError validateFreshNoShow(ReservationStatus status) {
        if (status == ReservationStatus.CONFIRMED || status == ReservationStatus.ARRIVED) {
            return null;
        }
        if (status == ReservationStatus.DRAFT) {
            return ReservationNoShowError.RESERVATION_CANNOT_NO_SHOW_DRAFT;
        }
        if (status == ReservationStatus.SEATED) {
            return ReservationNoShowError.RESERVATION_CANNOT_NO_SHOW_SEATED;
        }
        if (status == ReservationStatus.CANCELLED) {
            return ReservationNoShowError.RESERVATION_CANNOT_NO_SHOW_CANCELLED;
        }
        if (status == ReservationStatus.COMPLETED) {
            return ReservationNoShowError.RESERVATION_CANNOT_NO_SHOW_COMPLETED;
        }
        return ReservationNoShowError.ILLEGAL_STATE_TRANSITION;
    }

    private static Reservation markNoShow(Reservation reservation, Instant noShowAt, String reasonCode) {
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
            ReservationStatus.NO_SHOW,
            reservation.sourceChannel(),
            reservation.cancellationReasonCode(),
            trimToNull(reasonCode),
            reservation.note(),
            reservation.createdAt(),
            noShowAt,
            reservation.deletedAt()
        );
    }

    private static void require(RuleDecision decision, ReservationNoShowError fallback) {
        if (!decision.accepted()) {
            ReservationNoShowError error = ReservationNoShowError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == ReservationNoShowError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(Reservation reservation, Instant noShowAt, boolean alreadyNoShow) {
        return """
            {"reservationId":"%s","reservationCode":"%s","status":"no_show","noShowAt":"%s","noShowReasonCode":%s,"alreadyNoShow":%s}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            noShowAt,
            jsonNullable(reservation.noShowReasonCode()),
            alreadyNoShow
        ).trim();
    }

    private static String metadata(
        Reservation reservation,
        MarkReservationNoShowCommand command,
        ReservationStatus fromStatus,
        IdempotencyKey idempotencyKey,
        Instant noShowAt,
        boolean alreadyNoShow
    ) {
        return """
            {"reservationId":"%s","reservationCode":"%s","beforeStatus":"%s","afterStatus":"no_show","noShowAt":"%s","alreadyNoShow":%s,"source":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            fromStatus.code(),
            noShowAt,
            alreadyNoShow,
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

    private static String source(MarkReservationNoShowCommand command) {
        if (command == null || !hasText(command.actorType())) {
            return "staff";
        }
        return command.actorType().trim();
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
        private final ReservationNoShowError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationNoShowError error) {
            this(error, false);
        }

        private ApplicationFailure(ReservationNoShowError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationNoShowError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
