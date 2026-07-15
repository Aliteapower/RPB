package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.rule.DefaultAuditRule;
import com.rpb.reservation.audit.rule.DefaultBusinessEventRule;
import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.DefaultStoreAccessPolicy;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.AssignableReservationTable;
import com.rpb.reservation.reservation.application.AssignableReservationTablesResult;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentError;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentResult;
import com.rpb.reservation.reservation.application.command.AssignReservationTableCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.application.query.AssignableReservationTablesQuery;
import com.rpb.reservation.reservation.application.rule.ReservationTableAssignmentRule;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.table.application.DiningTableResourceRow;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTableAssignmentApplicationService {

    private static final String ACTION = "assign_reservation_table";
    private static final String TARGET_RESERVATION = "reservation";
    private static final String RESOURCE_DINING_TABLE = "dining_table";
    private static final String EVENT_TABLE_ASSIGNED = "reservation.table_assigned";
    private static final String OPERATION_TABLE_ASSIGN = "reservation.table_assign";

    private final StoreRepositoryPort storeRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final DiningTableRepositoryPort tableRepository;
    private final ReservationPreassignmentRepositoryPort preassignmentRepository;
    private final BusinessEventRepositoryPort businessEventRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final IdempotencyRepositoryPort idempotencyRepository;
    private final Clock clock;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();
    private final DefaultIdempotencyRule idempotencyRule = new DefaultIdempotencyRule();
    private final DefaultBusinessEventRule businessEventRule = new DefaultBusinessEventRule();
    private final DefaultAuditRule auditRule = new DefaultAuditRule();
    private final ReservationTableAssignmentRule assignmentRule = new ReservationTableAssignmentRule();

    @Autowired
    public ReservationTableAssignmentApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        DiningTableRepositoryPort tableRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        BusinessEventRepositoryPort businessEventRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.preassignmentRepository = preassignmentRepository;
        this.businessEventRepository = businessEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AssignableReservationTablesResult listAssignableTables(AssignableReservationTablesQuery query) {
        ReservationTableAssignmentError validationError = validateQuery(query);
        if (validationError != null) {
            return AssignableReservationTablesResult.failure(validationError);
        }

        try {
            StoreScope scope = scope(query.tenantId(), query.storeId());
            requireStoreAccess(scope, query.actorId(), query.actorType());
            Reservation reservation = reservationRepository.findById(scope, new ReservationId(query.reservationId()))
                .orElseThrow(() -> new ApplicationFailure(ReservationTableAssignmentError.RESERVATION_NOT_FOUND));
            requireScope(scope, reservation.scope());
            ReservationTableAssignmentError reservationError = assignmentRule.validateReservation(
                reservation,
                preassignmentRepository.findActiveAssignmentForReservation(scope, reservation.id().value()).isPresent()
            );
            require(reservationError);

            TimeRange requestedRange = reservationRange(reservation);
            Set<ReservationResourceAssignment> assignments = overlappingAssignments(scope, reservation, requestedRange);
            List<AssignableReservationTable> tables = tableRepository.findVisibleResourceRows(scope, null, reservation.partySize())
                .stream()
                .filter(row -> isEligible(row, reservation, assignments, requestedRange))
                .map(ReservationTableAssignmentApplicationService::toAssignableTable)
                .toList();
            return AssignableReservationTablesResult.success(
                reservation.id().value(),
                reservation.partySize().value(),
                tables
            );
        } catch (ApplicationFailure failure) {
            return AssignableReservationTablesResult.failure(failure.error());
        } catch (RuntimeException exception) {
            return AssignableReservationTablesResult.failure(ReservationTableAssignmentError.PERSISTENCE_ERROR);
        }
    }

    @Transactional
    public ReservationTableAssignmentResult assignTable(AssignReservationTableCommand command) {
        ReservationTableAssignmentError validationError = validateCommand(command);
        if (validationError != null) {
            return ReservationTableAssignmentResult.failure(validationError);
        }

        StoreScope scope = scope(command.tenantId(), command.storeId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String source = source(command);
        String requestHash = requestHash(command);
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByScopeActionKey(
            scope,
            source,
            ACTION,
            idempotencyKey
        );
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
            return ReservationTableAssignmentResult.failure(ReservationTableAssignmentError.PERSISTENCE_ERROR);
        }

        try {
            return executeAssignment(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            return failure.retryLater()
                ? ReservationTableAssignmentResult.retryLater(failure.error())
                : ReservationTableAssignmentResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationTableAssignmentError.PERSISTENCE_ERROR);
            return ReservationTableAssignmentResult.failure(ReservationTableAssignmentError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(AssignReservationTableCommand command) {
        String normalized = String.join(
            "|",
            value(command == null ? null : command.tenantId()),
            value(command == null ? null : command.storeId()),
            value(command == null ? null : command.reservationId()),
            value(command == null ? null : command.tableId()),
            value(command == null ? null : command.actorId()),
            normalize(command == null ? null : command.actorType()),
            normalize(command == null ? null : command.source())
        );
        return sha256(normalized);
    }

    private ReservationTableAssignmentResult executeAssignment(
        AssignReservationTableCommand command,
        StoreScope scope,
        IdempotencyRecord started
    ) {
        requireStoreAccess(scope, command.actorId(), command.actorType());
        Reservation reservation = reservationRepository.findByIdForUpdate(scope, new ReservationId(command.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationTableAssignmentError.RESERVATION_NOT_FOUND));
        requireScope(scope, reservation.scope());
        require(assignmentRule.validateReservation(reservation, false));

        Optional<ReservationResourceAssignment> current = preassignmentRepository.findActiveAssignmentForReservation(
            scope,
            reservation.id().value()
        );
        if (current.isPresent()) {
            ReservationResourceAssignment existing = current.get();
            if (RESOURCE_DINING_TABLE.equals(existing.resourceType()) && command.tableId().equals(existing.resourceId())) {
                IdempotencyRecord completed = completeIdempotency(
                    scope,
                    started,
                    reservation.id().value(),
                    existing.resourceId(),
                    existing.resourceCode()
                );
                return ReservationTableAssignmentResult.success(
                    reservation.id().value(),
                    existing.resourceId(),
                    existing.resourceCode(),
                    completed.status().code(),
                    null,
                    null
                );
            }
            throw new ApplicationFailure(ReservationTableAssignmentError.RESERVATION_ALREADY_ASSIGNED);
        }

        DiningTable table = tableRepository.findByIdForUpdate(scope, new TableId(command.tableId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationTableAssignmentError.TABLE_NOT_FOUND));
        requireScope(scope, table.scope());
        require(assignmentRule.validateTable(table, reservation.partySize()));

        TimeRange requestedRange = reservationRange(reservation);
        boolean conflict = overlappingAssignments(scope, reservation, requestedRange).stream()
            .anyMatch(assignment -> isConflict(assignment, reservation.id().value(), table.id().value(), requestedRange));
        if (conflict) {
            throw new ApplicationFailure(ReservationTableAssignmentError.TABLE_NOT_AVAILABLE);
        }

        ReservationPreassignment preassignment = savePreassignment(scope, reservation, table);
        BusinessEvent event = appendBusinessEvent(scope, command, reservation, table, started.idempotencyKey());
        AuditLog audit = appendAudit(scope, command, reservation, table, preassignment, started.idempotencyKey());
        IdempotencyRecord completed = completeIdempotency(
            scope,
            started,
            reservation.id().value(),
            table.id().value(),
            table.tableCode()
        );
        return ReservationTableAssignmentResult.success(
            reservation.id().value(),
            table.id().value(),
            table.tableCode(),
            completed.status().code(),
            event.id(),
            audit.id()
        );
    }

    private void requireStoreAccess(StoreScope scope, UUID actorId, String actorType) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationTableAssignmentError.STORE_NOT_FOUND));
        requireScope(scope, store.scope());
        RuleDecision decision = storeAccessPolicy.decide(scope, actorId, actorType);
        if (!decision.accepted()) {
            throw new ApplicationFailure(ReservationTableAssignmentError.FORBIDDEN);
        }
    }

    private ReservationPreassignment savePreassignment(StoreScope scope, Reservation reservation, DiningTable table) {
        try {
            return preassignmentRepository.save(
                scope,
                new ReservationPreassignment(
                    UUID.randomUUID(),
                    scope,
                    reservation.id(),
                    RESOURCE_DINING_TABLE,
                    table.id().value(),
                    "active"
                )
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationTableAssignmentError.PERSISTENCE_ERROR);
        }
    }

    private BusinessEvent appendBusinessEvent(
        StoreScope scope,
        AssignReservationTableCommand command,
        Reservation reservation,
        DiningTable table,
        IdempotencyKey idempotencyKey
    ) {
        BusinessEvent event = new BusinessEvent(
            UUID.randomUUID(),
            EVENT_TABLE_ASSIGNED,
            TARGET_RESERVATION,
            reservation.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, table, idempotencyKey)
        );
        if (!businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()).accepted()) {
            throw new ApplicationFailure(ReservationTableAssignmentError.BUSINESS_EVENT_WRITE_FAILED);
        }
        try {
            return businessEventRepository.append(scope, event);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationTableAssignmentError.BUSINESS_EVENT_WRITE_FAILED);
        }
    }

    private AuditLog appendAudit(
        StoreScope scope,
        AssignReservationTableCommand command,
        Reservation reservation,
        DiningTable table,
        ReservationPreassignment preassignment,
        IdempotencyKey idempotencyKey
    ) {
        AuditLog audit = new AuditLog(
            UUID.randomUUID(),
            OPERATION_TABLE_ASSIGN,
            TARGET_RESERVATION,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, table, idempotencyKey)
                .replace("}", ",\"preassignmentId\":\"" + preassignment.id() + "\"}")
        );
        if (!auditRule.evaluate(audit.operationCode(), audit.targetType(), audit.targetId(), audit.actorType()).accepted()) {
            throw new ApplicationFailure(ReservationTableAssignmentError.AUDIT_WRITE_FAILED);
        }
        try {
            return auditLogRepository.append(scope, audit);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationTableAssignmentError.AUDIT_WRITE_FAILED);
        }
    }

    private IdempotencyRecord completeIdempotency(
        StoreScope scope,
        IdempotencyRecord started,
        UUID reservationId,
        UUID tableId,
        String tableCode
    ) {
        IdempotencyRecord payload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            TARGET_RESERVATION,
            reservationId,
            snapshot(reservationId, tableId, tableCode)
        );
        try {
            return idempotencyRepository.complete(scope, payload, TARGET_RESERVATION);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationTableAssignmentError.PERSISTENCE_ERROR);
        }
    }

    private ReservationTableAssignmentResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationTableAssignmentError error = ReservationTableAssignmentError.fromCode(decision.violationCode());
            return error == ReservationTableAssignmentError.COMMAND_IN_PROGRESS
                ? ReservationTableAssignmentResult.retryLater(error)
                : ReservationTableAssignmentResult.failure(error);
        }
        if (existing.status() != IdempotencyStatus.COMPLETED) {
            return ReservationTableAssignmentResult.failure(ReservationTableAssignmentError.IDEMPOTENCY_CONFLICT);
        }
        try {
            return ReservationTableAssignmentResult.replay(
                UUID.fromString(extract(existing.responseSnapshot(), "reservationId")),
                UUID.fromString(extract(existing.responseSnapshot(), "tableId")),
                extract(existing.responseSnapshot(), "tableCode")
            );
        } catch (RuntimeException exception) {
            return ReservationTableAssignmentResult.failure(ReservationTableAssignmentError.IDEMPOTENCY_CONFLICT);
        }
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationTableAssignmentError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private Set<ReservationResourceAssignment> overlappingAssignments(
        StoreScope scope,
        Reservation reservation,
        TimeRange requestedRange
    ) {
        Set<ReservationResourceAssignment> assignments = preassignmentRepository.findActiveResourceAssignmentsOverlapping(
            scope,
            reservation.businessDate(),
            requestedRange
        );
        return assignments == null ? Set.of() : assignments;
    }

    private static boolean isEligible(
        DiningTableResourceRow row,
        Reservation reservation,
        Set<ReservationResourceAssignment> assignments,
        TimeRange requestedRange
    ) {
        if (row == null || row.resourceId() == null || "inactive".equalsIgnoreCase(row.status())) {
            return false;
        }
        int partySize = reservation.partySize().value();
        if (partySize < row.capacityMin() || partySize > row.capacityMax()) {
            return false;
        }
        return assignments.stream()
            .noneMatch(assignment -> isConflict(assignment, reservation.id().value(), row.resourceId(), requestedRange));
    }

    private static boolean isConflict(
        ReservationResourceAssignment assignment,
        UUID reservationId,
        UUID tableId,
        TimeRange requestedRange
    ) {
        if (
            assignment == null
                || reservationId.equals(assignment.reservationId())
                || !RESOURCE_DINING_TABLE.equals(assignment.resourceType())
                || !tableId.equals(assignment.resourceId())
                || assignment.reservedStartAt() == null
                || assignment.reservedEndAt() == null
        ) {
            return false;
        }
        return assignment.reservedStartAt().isBefore(requestedRange.end())
            && assignment.reservedEndAt().isAfter(requestedRange.start());
    }

    private static AssignableReservationTable toAssignableTable(DiningTableResourceRow row) {
        return new AssignableReservationTable(
            row.resourceId(),
            row.code(),
            row.displayName(),
            row.areaName(),
            row.capacityMin(),
            row.capacityMax()
        );
    }

    private static TimeRange reservationRange(Reservation reservation) {
        if (reservation.businessDate() == null || reservation.reservedStartAt() == null || reservation.reservedEndAt() == null) {
            throw new ApplicationFailure(ReservationTableAssignmentError.RESERVATION_NOT_ASSIGNABLE);
        }
        try {
            return new TimeRange(reservation.reservedStartAt(), reservation.reservedEndAt());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationFailure(ReservationTableAssignmentError.RESERVATION_NOT_ASSIGNABLE);
        }
    }

    private static ReservationTableAssignmentError validateCommand(AssignReservationTableCommand command) {
        if (command == null) {
            return ReservationTableAssignmentError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationTableAssignmentError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.tableId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationTableAssignmentError.INVALID_COMMAND;
        }
        return null;
    }

    private static ReservationTableAssignmentError validateQuery(AssignableReservationTablesQuery query) {
        if (
            query == null
                || query.tenantId() == null
                || query.storeId() == null
                || query.reservationId() == null
                || query.actorId() == null
                || !hasText(query.actorType())
        ) {
            return ReservationTableAssignmentError.INVALID_COMMAND;
        }
        return null;
    }

    private static StoreScope scope(UUID tenantId, UUID storeId) {
        return new StoreScope(new TenantId(tenantId), new com.rpb.reservation.store.value.StoreId(storeId));
    }

    private static void requireScope(StoreScope expected, StoreScope actual) {
        if (!expected.equals(actual)) {
            throw new ApplicationFailure(ReservationTableAssignmentError.STORE_SCOPE_MISMATCH);
        }
    }

    private static void require(ReservationTableAssignmentError error) {
        if (error != null) {
            throw new ApplicationFailure(error);
        }
    }

    private static String source(AssignReservationTableCommand command) {
        return OperationSource.fromSourceOrActor(command == null ? null : command.source(), command == null ? null : command.actorType());
    }

    private static String snapshot(UUID reservationId, UUID tableId, String tableCode) {
        return """
            {"reservationId":"%s","tableId":"%s","tableCode":"%s","assignmentStatus":"active"}
            """.formatted(reservationId, tableId, escape(tableCode)).trim();
    }

    private static String metadata(Reservation reservation, DiningTable table, IdempotencyKey idempotencyKey) {
        return """
            {"reservationId":"%s","reservationCode":"%s","tableId":"%s","tableCode":"%s","reservationStatus":"%s","idempotencyKey":"%s"}
            """.formatted(
            reservation.id().value(),
            escape(reservation.reservationCode().value()),
            table.id().value(),
            escape(table.tableCode()),
            reservation.status().code(),
            escape(idempotencyKey.value())
        ).trim();
    }

    private static String extract(String json, String key) {
        if (json == null) {
            throw new IllegalArgumentException("snapshot_missing");
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String sha256(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha_256_unavailable", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final ReservationTableAssignmentError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationTableAssignmentError error) {
            this(error, false);
        }

        private ApplicationFailure(ReservationTableAssignmentError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationTableAssignmentError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
