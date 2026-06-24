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
import com.rpb.reservation.reservation.application.ReservationCheckInError;
import com.rpb.reservation.reservation.application.ReservationCheckInResult;
import com.rpb.reservation.reservation.application.command.CheckInReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.rule.ReservationCheckInRule;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
public class ReservationCheckInApplicationService {

    private static final String ACTION = "check_in_reservation";
    private static final String TARGET_RESERVATION = "reservation";
    private static final String EVENT_RESERVATION_ARRIVED = "reservation.arrived";
    private static final String OPERATION_CHECK_IN = "reservation.check_in";

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
    private final ReservationCheckInRule reservationCheckInRule = new ReservationCheckInRule();

    @Autowired
    public ReservationCheckInApplicationService(
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
    public ReservationCheckInResult checkInReservation(CheckInReservationCommand command) {
        ReservationCheckInError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return ReservationCheckInResult.failure(preValidationError);
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
            return ReservationCheckInResult.failure(ReservationCheckInError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? ReservationCheckInResult.retryLater(failure.error())
                : ReservationCheckInResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationCheckInError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, started.idempotencyKey(), ReservationCheckInError.REPOSITORY_SAVE_FAILED);
            return ReservationCheckInResult.failure(ReservationCheckInError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(CheckInReservationCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.reservationId()),
            value(command.actorId()),
            normalize(command.actorType()),
            command.arrivedAt() == null ? "application_clock" : value(command.arrivedAt()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private ReservationCheckInResult execute(CheckInReservationCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationCheckInError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(ReservationCheckInError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), ReservationCheckInError.STORE_ACCESS_DENIED);

        Reservation reservation = reservationRepository.findById(scope, new ReservationId(command.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationCheckInError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(ReservationCheckInError.STORE_SCOPE_MISMATCH);
        }
        requireReservationForStoreToday(store, reservation);

        ReservationCheckInError statusError = reservationCheckInRule.validate(reservation.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }

        if (reservation.status() == ReservationStatus.ARRIVED) {
            Instant arrivalEvidence = existingArrivedAt(scope, reservation)
                .orElseGet(() -> command.arrivedAt() == null ? Instant.now(clock) : command.arrivedAt());
            IdempotencyRecord completed = completeIdempotency(scope, started, reservation, arrivalEvidence, true);
            return ReservationCheckInResult.alreadyArrived(
                reservation.id().value(),
                reservation.reservationCode().value(),
                arrivalEvidence,
                completed.status().code()
            );
        }

        Instant arrivedAt = command.arrivedAt() == null ? Instant.now(clock) : command.arrivedAt();
        if (!reservationStateMachine.canTransition(ReservationStatus.CONFIRMED, ReservationStatus.ARRIVED)) {
            throw new ApplicationFailure(ReservationCheckInError.ILLEGAL_STATE_TRANSITION);
        }

        Reservation arrived = arrive(reservation, arrivedAt);
        Reservation saved = saveArrived(scope, arrived);
        BusinessEvent event = appendBusinessEvent(scope, command, saved, started.idempotencyKey(), arrivedAt);
        StateTransitionLog transition = appendTransitionLog(scope, command, saved, started.idempotencyKey(), arrivedAt);
        AuditLog auditLog = appendCompletedAudit(scope, command, saved, started.idempotencyKey(), arrivedAt);
        IdempotencyRecord completed = completeIdempotency(scope, started, saved, arrivedAt, false);

        return ReservationCheckInResult.success(
            saved.id().value(),
            saved.reservationCode().value(),
            saved.status().code(),
            arrivedAt,
            completed.status().code(),
            List.of(EVENT_RESERVATION_ARRIVED),
            List.of(event.id()),
            List.of(transition.id()),
            auditLog.id()
        );
    }

    private void requireReservationForStoreToday(Store store, Reservation reservation) {
        LocalDate storeToday = LocalDate.now(clock.withZone(ZoneId.of(store.timezone())));
        if (!reservation.businessDate().value().equals(storeToday)) {
            throw new ApplicationFailure(ReservationCheckInError.RESERVATION_NOT_TODAY);
        }
    }

    private Reservation saveArrived(StoreScope scope, Reservation reservation) {
        try {
            return reservationRepository.save(scope, reservation);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCheckInError.REPOSITORY_SAVE_FAILED);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        CheckInReservationCommand command,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant arrivedAt
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_RESERVATION_ARRIVED,
            TARGET_RESERVATION,
            reservation.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, idempotencyKey, arrivedAt, false)
        );
        require(
            businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
            ReservationCheckInError.BUSINESS_EVENT_WRITE_FAILED
        );
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCheckInError.BUSINESS_EVENT_WRITE_FAILED);
        }
    }

    private StateTransitionLog appendTransitionLog(
        StoreScope scope,
        CheckInReservationCommand command,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant arrivedAt
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_RESERVATION,
            reservation.id().value(),
            ReservationStatus.CONFIRMED.code(),
            ReservationStatus.ARRIVED.code(),
            OPERATION_CHECK_IN,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, idempotencyKey, arrivedAt, false)
        );
        require(
            stateTransitionRule.evaluate(
                transition.targetType(),
                transition.targetId(),
                transition.toStatus(),
                transition.transitionCode(),
                transition.actorType()
            ),
            ReservationCheckInError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return stateTransitionLogRepository.append(scope, transition);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCheckInError.STATE_TRANSITION_WRITE_FAILED);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        CheckInReservationCommand command,
        Reservation reservation,
        IdempotencyKey idempotencyKey,
        Instant arrivedAt
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_CHECK_IN,
            TARGET_RESERVATION,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, command, idempotencyKey, arrivedAt, false)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), ReservationCheckInError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCheckInError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        CheckInReservationCommand command,
        IdempotencyKey idempotencyKey,
        ReservationCheckInError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    "reservation.check_in.failed",
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
        Instant arrivedAt,
        boolean alreadyArrived
    ) {
        String snapshot = snapshot(reservation, arrivedAt, alreadyArrived);
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

    private ReservationCheckInResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationCheckInError error = ReservationCheckInError.fromCode(decision.violationCode());
            if (error == ReservationCheckInError.COMMAND_IN_PROGRESS) {
                return ReservationCheckInResult.retryLater(error);
            }
            return ReservationCheckInResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return ReservationCheckInResult.failure(ReservationCheckInError.IDEMPOTENCY_CONFLICT);
            }
        }
        return ReservationCheckInResult.failure(ReservationCheckInError.IDEMPOTENCY_CONFLICT);
    }

    private ReservationCheckInResult replay(String snapshot) {
        return ReservationCheckInResult.replay(
            UUID.fromString(extract(snapshot, "reservationId")),
            extract(snapshot, "reservationCode"),
            extract(snapshot, "status"),
            Instant.parse(extract(snapshot, "arrivedAt")),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyArrived"))
        );
    }

    private Optional<Instant> existingArrivedAt(StoreScope scope, Reservation reservation) {
        return stateTransitionLogRepository.findLatest(scope, TARGET_RESERVATION, reservation.id().value())
            .filter(transition -> ReservationStatus.ARRIVED.code().equals(transition.toStatus()))
            .flatMap(transition -> extractInstant(transition.metadata(), "arrivedAt"));
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationCheckInError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static ReservationCheckInError validateCommand(CheckInReservationCommand command) {
        if (command == null) {
            return ReservationCheckInError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationCheckInError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationCheckInError.INVALID_COMMAND;
        }
        return null;
    }

    private static Reservation arrive(Reservation reservation, Instant arrivedAt) {
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
            ReservationStatus.ARRIVED,
            reservation.sourceChannel(),
            reservation.cancellationReasonCode(),
            reservation.noShowReasonCode(),
            reservation.note(),
            reservation.createdAt(),
            arrivedAt,
            reservation.deletedAt()
        );
    }

    private static void require(RuleDecision decision, ReservationCheckInError fallback) {
        if (!decision.accepted()) {
            ReservationCheckInError error = ReservationCheckInError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == ReservationCheckInError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(Reservation reservation, Instant arrivedAt, boolean alreadyArrived) {
        return """
            {"reservationId":"%s","reservationCode":"%s","status":"arrived","arrivedAt":"%s","alreadyArrived":%s}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            arrivedAt,
            alreadyArrived
        ).trim();
    }

    private static String metadata(
        Reservation reservation,
        CheckInReservationCommand command,
        IdempotencyKey idempotencyKey,
        Instant arrivedAt,
        boolean alreadyArrived
    ) {
        return """
            {"reservationId":"%s","reservationCode":"%s","beforeStatus":"confirmed","afterStatus":"arrived","arrivedAt":"%s","alreadyArrived":%s,"source":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            arrivedAt,
            alreadyArrived,
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

    private static String source(CheckInReservationCommand command) {
        if (command == null || !hasText(command.actorType())) {
            return "staff";
        }
        return command.actorType().trim();
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
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
        private final ReservationCheckInError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationCheckInError error) {
            this(error, false);
        }

        private ApplicationFailure(ReservationCheckInError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationCheckInError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
