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
import com.rpb.reservation.reservation.application.ReservationCompleteError;
import com.rpb.reservation.reservation.application.ReservationCompleteResult;
import com.rpb.reservation.reservation.application.command.CompleteReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.state.ReservationStateMachine;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.state.SeatingStateMachine;
import com.rpb.reservation.seating.status.SeatingStatus;
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
public class ReservationCompleteApplicationService {
    private static final String ACTION = "complete_reservation";
    private static final String TARGET_RESERVATION = "reservation";
    private static final String TARGET_SEATING = "seating";
    private static final String EVENT_RESERVATION_COMPLETED = "reservation.completed";
    private static final String EVENT_SEATING_COMPLETED = "seating.completed";
    private static final String OPERATION_COMPLETE = "reservation.complete";
    private static final String OPERATION_COMPLETE_FAILED = "reservation.complete.failed";
    private static final String TRANSITION_SEATING_COMPLETE = "seating.complete";

    private final StoreRepositoryPort storeRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final SeatingRepositoryPort seatingRepository;
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
    private final SeatingStateMachine seatingStateMachine = new SeatingStateMachine();

    @Autowired
    public ReservationCompleteApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        SeatingRepositoryPort seatingRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this(
            storeRepository,
            reservationRepository,
            seatingRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            Clock.systemUTC()
        );
    }

    public ReservationCompleteApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        SeatingRepositoryPort seatingRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.reservationRepository = reservationRepository;
        this.seatingRepository = seatingRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationCompleteResult completeReservation(CompleteReservationCommand command) {
        ReservationCompleteError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return ReservationCompleteResult.failure(preValidationError);
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
            return ReservationCompleteResult.failure(ReservationCompleteError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? ReservationCompleteResult.retryLater(failure.error())
                : ReservationCompleteResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationCompleteError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, started.idempotencyKey(), ReservationCompleteError.REPOSITORY_SAVE_FAILED);
            return ReservationCompleteResult.failure(ReservationCompleteError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(CompleteReservationCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.reservationId()),
            value(command.actorId()),
            normalize(command.actorType()),
            command.completedAt() == null ? "application_clock" : value(command.completedAt()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private ReservationCompleteResult execute(
        CompleteReservationCommand command,
        StoreScope scope,
        IdempotencyRecord started
    ) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationCompleteError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(ReservationCompleteError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), ReservationCompleteError.STORE_ACCESS_DENIED);

        Reservation reservation = reservationRepository.findById(scope, new ReservationId(command.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationCompleteError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(ReservationCompleteError.STORE_SCOPE_MISMATCH);
        }

        if (reservation.status() == ReservationStatus.COMPLETED) {
            Seating seating = seatingRepository.findCurrentByReservation(scope, reservation.id())
                .orElse(null);
            Instant evidence = existingCompletedAt(scope, reservation)
                .orElseGet(() -> reservation.updatedAt() == null ? Instant.now(clock) : reservation.updatedAt());
            IdempotencyRecord completed = completeIdempotency(scope, started, reservation, evidence, seating, true);
            return ReservationCompleteResult.alreadyCompleted(
                reservation.id().value(),
                reservation.reservationCode().value(),
                evidence,
                seating == null ? null : seating.id().value(),
                seating == null ? null : seating.status().code(),
                completed.status().code()
            );
        }

        ReservationCompleteError statusError = validateFreshComplete(reservation.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }
        if (!reservationStateMachine.canTransition(reservation.status(), ReservationStatus.COMPLETED)) {
            throw new ApplicationFailure(ReservationCompleteError.ILLEGAL_STATE_TRANSITION);
        }

        Seating seating = seatingRepository.findCurrentByReservation(scope, reservation.id())
            .orElseThrow(() -> new ApplicationFailure(ReservationCompleteError.RESERVATION_COMPLETED_WITHOUT_ACTIVE_SEATING));
        if (seating.status() != SeatingStatus.OCCUPIED) {
            throw new ApplicationFailure(ReservationCompleteError.RESERVATION_COMPLETED_WITHOUT_ACTIVE_SEATING);
        }
        if (!seatingStateMachine.canTransition(seating.status(), SeatingStatus.COMPLETED)) {
            throw new ApplicationFailure(ReservationCompleteError.ILLEGAL_STATE_TRANSITION);
        }

        Instant completedAt = command.completedAt() == null ? Instant.now(clock) : command.completedAt();
        Reservation completedReservation = completeReservation(reservation, completedAt);
        Seating completedSeating = completeSeating(seating, completedAt);
        Reservation savedReservation = saveReservation(scope, completedReservation);
        Seating savedSeating = saveSeating(scope, completedSeating);
        List<BusinessEvent> events = appendBusinessEvents(
            scope,
            command,
            reservation.status(),
            savedReservation,
            seating.status(),
            savedSeating,
            started.idempotencyKey(),
            completedAt
        );
        List<StateTransitionLog> transitions = appendTransitionLogs(
            scope,
            command,
            reservation.status(),
            savedReservation,
            seating.status(),
            savedSeating,
            started.idempotencyKey(),
            completedAt
        );
        AuditLog auditLog = appendCompletedAudit(
            scope,
            command,
            reservation.status(),
            savedReservation,
            seating.status(),
            savedSeating,
            started.idempotencyKey(),
            completedAt
        );
        IdempotencyRecord completed = completeIdempotency(scope, started, savedReservation, completedAt, savedSeating, false);

        return ReservationCompleteResult.success(
            savedReservation.id().value(),
            savedReservation.reservationCode().value(),
            savedReservation.status().code(),
            completedAt,
            savedSeating.id().value(),
            savedSeating.status().code(),
            completed.status().code(),
            List.of(EVENT_RESERVATION_COMPLETED, EVENT_SEATING_COMPLETED),
            events.stream().map(BusinessEvent::id).toList(),
            transitions.stream().map(StateTransitionLog::id).toList(),
            auditLog.id()
        );
    }

    private Reservation saveReservation(StoreScope scope, Reservation reservation) {
        try {
            return reservationRepository.save(scope, reservation);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCompleteError.REPOSITORY_SAVE_FAILED);
        }
    }

    private Seating saveSeating(StoreScope scope, Seating seating) {
        try {
            return seatingRepository.save(scope, seating);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCompleteError.REPOSITORY_SAVE_FAILED);
        }
    }

    private List<BusinessEvent> appendBusinessEvents(
        StoreScope scope,
        CompleteReservationCommand command,
        ReservationStatus fromReservationStatus,
        Reservation reservation,
        SeatingStatus fromSeatingStatus,
        Seating seating,
        IdempotencyKey idempotencyKey,
        Instant completedAt
    ) {
        String metadata = metadata(
            reservation,
            seating,
            command,
            fromReservationStatus,
            fromSeatingStatus,
            idempotencyKey,
            completedAt,
            false
        );
        List<BusinessEvent> events = List.of(
            new BusinessEvent(
                UUID.randomUUID(),
                EVENT_RESERVATION_COMPLETED,
                TARGET_RESERVATION,
                reservation.id().value(),
                command.actorType(),
                command.actorId(),
                source(command),
                metadata
            ),
            new BusinessEvent(
                UUID.randomUUID(),
                EVENT_SEATING_COMPLETED,
                TARGET_SEATING,
                seating.id().value(),
                command.actorType(),
                command.actorId(),
                source(command),
                metadata
            )
        );
        for (BusinessEvent event : events) {
            require(
                businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
                ReservationCompleteError.BUSINESS_EVENT_WRITE_FAILED
            );
        }
        try {
            return events.stream().map(event -> businessEventRepository.append(scope, event)).toList();
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCompleteError.BUSINESS_EVENT_WRITE_FAILED);
        }
    }

    private List<StateTransitionLog> appendTransitionLogs(
        StoreScope scope,
        CompleteReservationCommand command,
        ReservationStatus fromReservationStatus,
        Reservation reservation,
        SeatingStatus fromSeatingStatus,
        Seating seating,
        IdempotencyKey idempotencyKey,
        Instant completedAt
    ) {
        String metadata = metadata(
            reservation,
            seating,
            command,
            fromReservationStatus,
            fromSeatingStatus,
            idempotencyKey,
            completedAt,
            false
        );
        List<StateTransitionLog> transitions = List.of(
            new StateTransitionLog(
                UUID.randomUUID(),
                TARGET_RESERVATION,
                reservation.id().value(),
                fromReservationStatus.code(),
                ReservationStatus.COMPLETED.code(),
                OPERATION_COMPLETE,
                command.actorType(),
                command.actorId(),
                source(command),
                metadata
            ),
            new StateTransitionLog(
                UUID.randomUUID(),
                TARGET_SEATING,
                seating.id().value(),
                fromSeatingStatus.code(),
                SeatingStatus.COMPLETED.code(),
                TRANSITION_SEATING_COMPLETE,
                command.actorType(),
                command.actorId(),
                source(command),
                metadata
            )
        );
        for (StateTransitionLog transition : transitions) {
            require(
                stateTransitionRule.evaluate(
                    transition.targetType(),
                    transition.targetId(),
                    transition.toStatus(),
                    transition.transitionCode(),
                    transition.actorType()
                ),
                ReservationCompleteError.STATE_TRANSITION_WRITE_FAILED
            );
        }
        try {
            return transitions.stream().map(transition -> stateTransitionLogRepository.append(scope, transition)).toList();
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCompleteError.STATE_TRANSITION_WRITE_FAILED);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        CompleteReservationCommand command,
        ReservationStatus fromReservationStatus,
        Reservation reservation,
        SeatingStatus fromSeatingStatus,
        Seating seating,
        IdempotencyKey idempotencyKey,
        Instant completedAt
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_COMPLETE,
            TARGET_RESERVATION,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, seating, command, fromReservationStatus, fromSeatingStatus, idempotencyKey, completedAt, false)
        );
        require(
            auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()),
            ReservationCompleteError.AUDIT_WRITE_FAILED
        );
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCompleteError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        CompleteReservationCommand command,
        IdempotencyKey idempotencyKey,
        ReservationCompleteError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_COMPLETE_FAILED,
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
        Instant completedAt,
        Seating seating,
        boolean alreadyCompleted
    ) {
        String snapshot = snapshot(reservation, completedAt, seating, alreadyCompleted);
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

    private ReservationCompleteResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationCompleteError error = ReservationCompleteError.fromCode(decision.violationCode());
            if (error == ReservationCompleteError.COMMAND_IN_PROGRESS) {
                return ReservationCompleteResult.retryLater(error);
            }
            return ReservationCompleteResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return ReservationCompleteResult.failure(ReservationCompleteError.IDEMPOTENCY_CONFLICT);
            }
        }
        return ReservationCompleteResult.failure(ReservationCompleteError.IDEMPOTENCY_CONFLICT);
    }

    private ReservationCompleteResult replay(String snapshot) {
        return ReservationCompleteResult.replay(
            UUID.fromString(extract(snapshot, "reservationId")),
            extract(snapshot, "reservationCode"),
            extract(snapshot, "status"),
            Instant.parse(extract(snapshot, "completedAt")),
            extractNullableUuid(snapshot, "seatingId"),
            extractNullable(snapshot, "seatingStatus"),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyCompleted"))
        );
    }

    private Optional<Instant> existingCompletedAt(StoreScope scope, Reservation reservation) {
        return stateTransitionLogRepository.findLatest(scope, TARGET_RESERVATION, reservation.id().value())
            .filter(transition -> ReservationStatus.COMPLETED.code().equals(transition.toStatus()))
            .flatMap(transition -> extractInstant(transition.metadata(), "completedAt"));
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationCompleteError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static ReservationCompleteError validateCommand(CompleteReservationCommand command) {
        if (command == null) {
            return ReservationCompleteError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationCompleteError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationCompleteError.INVALID_COMMAND;
        }
        return null;
    }

    private static ReservationCompleteError validateFreshComplete(ReservationStatus status) {
        if (status == ReservationStatus.SEATED) {
            return null;
        }
        if (status == ReservationStatus.DRAFT) {
            return ReservationCompleteError.RESERVATION_CANNOT_COMPLETE_DRAFT;
        }
        if (status == ReservationStatus.CONFIRMED) {
            return ReservationCompleteError.RESERVATION_CANNOT_COMPLETE_CONFIRMED;
        }
        if (status == ReservationStatus.ARRIVED) {
            return ReservationCompleteError.RESERVATION_CANNOT_COMPLETE_ARRIVED;
        }
        if (status == ReservationStatus.CANCELLED) {
            return ReservationCompleteError.RESERVATION_CANNOT_COMPLETE_CANCELLED;
        }
        if (status == ReservationStatus.NO_SHOW) {
            return ReservationCompleteError.RESERVATION_CANNOT_COMPLETE_NO_SHOW;
        }
        return ReservationCompleteError.ILLEGAL_STATE_TRANSITION;
    }

    private static Reservation completeReservation(Reservation reservation, Instant completedAt) {
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
            ReservationStatus.COMPLETED,
            reservation.sourceChannel(),
            reservation.cancellationReasonCode(),
            reservation.noShowReasonCode(),
            reservation.note(),
            reservation.createdAt(),
            completedAt,
            reservation.deletedAt()
        );
    }

    private static Seating completeSeating(Seating seating, Instant completedAt) {
        return new Seating(
            seating.id(),
            seating.scope(),
            seating.sourceType(),
            seating.sourceId(),
            seating.seatingCode(),
            seating.manualOverrideReasonCode(),
            seating.note(),
            seating.partySizeSnapshot(),
            SeatingStatus.COMPLETED,
            completedAt
        );
    }

    private static void require(RuleDecision decision, ReservationCompleteError fallback) {
        if (!decision.accepted()) {
            ReservationCompleteError error = ReservationCompleteError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == ReservationCompleteError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(
        Reservation reservation,
        Instant completedAt,
        Seating seating,
        boolean alreadyCompleted
    ) {
        return """
            {"reservationId":"%s","reservationCode":"%s","status":"completed","completedAt":"%s","seatingId":%s,"seatingStatus":%s,"alreadyCompleted":%s}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            completedAt,
            seating == null ? "null" : "\"" + seating.id().value() + "\"",
            seating == null ? "null" : jsonNullable(seating.status().code()),
            alreadyCompleted
        ).trim();
    }

    private static String metadata(
        Reservation reservation,
        Seating seating,
        CompleteReservationCommand command,
        ReservationStatus fromReservationStatus,
        SeatingStatus fromSeatingStatus,
        IdempotencyKey idempotencyKey,
        Instant completedAt,
        boolean alreadyCompleted
    ) {
        return """
            {"reservationId":"%s","reservationCode":"%s","seatingId":"%s","beforeReservationStatus":"%s","afterReservationStatus":"completed","beforeSeatingStatus":"%s","afterSeatingStatus":"completed","completedAt":"%s","alreadyCompleted":%s,"source":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            seating.id().value(),
            fromReservationStatus.code(),
            fromSeatingStatus.code(),
            completedAt,
            alreadyCompleted,
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

    private static UUID extractNullableUuid(String json, String key) {
        String value = extractNullable(json, key);
        return value == null ? null : UUID.fromString(value);
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

    private static String source(CompleteReservationCommand command) {
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
        private final ReservationCompleteError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationCompleteError error) {
            this(error, false);
        }

        private ApplicationFailure(ReservationCompleteError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationCompleteError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
