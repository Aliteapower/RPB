package com.rpb.reservation.table.application.service;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.audit.rule.DefaultAuditRule;
import com.rpb.reservation.audit.rule.DefaultBusinessEventRule;
import com.rpb.reservation.audit.rule.DefaultStateTransitionRule;
import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.cleaning.application.validator.CleaningResourceValidator;
import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.DefaultStoreAccessPolicy;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.validator.DefaultSeatingResourceValidator;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.table.application.TableSwitchError;
import com.rpb.reservation.table.application.TableSwitchResult;
import com.rpb.reservation.table.application.command.SwitchTableCommand;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.group.rule.DefaultTableGroupValidationRule;
import com.rpb.reservation.table.rule.DefaultTableAvailabilityRule;
import com.rpb.reservation.table.rule.DefaultTableLockRule;
import com.rpb.reservation.table.state.DiningTableStateMachine;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableSwitchApplicationService {

    private static final String ACTION = "switch_table";
    private static final String RESOURCE_TABLE = "dining_table";
    private static final String RESOURCE_GROUP = "table_group";
    private static final String EVENT_SWITCH_COMPLETED = "table.switch.completed";
    private static final String EVENT_TABLE_AVAILABLE = "table.available";
    private static final String EVENT_TABLE_OCCUPIED = "table.occupied";
    private static final String OPERATION_SWITCH_COMPLETED = "table.switch.completed";
    private static final String OPERATION_SWITCH_FAILED = "table.switch.failed";

    private final StoreRepositoryPort storeRepository;
    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final TableLockRepositoryPort tableLockRepository;
    private final SeatingRepositoryPort seatingRepository;
    private final CleaningRepositoryPort cleaningRepository;
    private final BusinessEventRepositoryPort businessEventRepository;
    private final StateTransitionLogRepositoryPort stateTransitionLogRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final IdempotencyRepositoryPort idempotencyRepository;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();
    private final DefaultTableAvailabilityRule tableAvailabilityRule = new DefaultTableAvailabilityRule();
    private final DefaultTableLockRule tableLockRule = new DefaultTableLockRule();
    private final DefaultTableGroupValidationRule tableGroupValidationRule = new DefaultTableGroupValidationRule();
    private final DefaultSeatingResourceValidator seatingResourceValidator = new DefaultSeatingResourceValidator();
    private final CleaningResourceValidator cleaningResourceValidator = new CleaningResourceValidator();
    private final DefaultAuditRule auditRule = new DefaultAuditRule();
    private final DefaultBusinessEventRule businessEventRule = new DefaultBusinessEventRule();
    private final DefaultStateTransitionRule stateTransitionRule = new DefaultStateTransitionRule();
    private final DefaultIdempotencyRule idempotencyRule = new DefaultIdempotencyRule();
    private final DiningTableStateMachine diningTableStateMachine = new DiningTableStateMachine();

    public TableSwitchApplicationService(
        StoreRepositoryPort storeRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        TableLockRepositoryPort tableLockRepository,
        SeatingRepositoryPort seatingRepository,
        CleaningRepositoryPort cleaningRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this.storeRepository = storeRepository;
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
        this.tableLockRepository = tableLockRepository;
        this.seatingRepository = seatingRepository;
        this.cleaningRepository = cleaningRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public TableSwitchResult switchTable(SwitchTableCommand command) {
        TableSwitchError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return TableSwitchResult.failure(preValidationError);
        }

        StoreScope scope = new StoreScope(new TenantId(command.tenantId()), command.storeId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String requestHash = requestHash(command);
        String source = source(command.actorType());

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByScopeActionKey(scope, source, ACTION, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExistingIdempotency(existing.get(), requestHash);
        }

        IdempotencyRecord started;
        try {
            started = idempotencyRepository.start(scope, source, ACTION, idempotencyKey, requestHash, OffsetDateTime.now().plusMinutes(30));
        } catch (RuntimeException exception) {
            return TableSwitchResult.failure(TableSwitchError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? TableSwitchResult.retryLater(failure.error())
                : TableSwitchResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, TableSwitchError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, started.idempotencyKey(), TableSwitchError.REPOSITORY_SAVE_FAILED);
            return TableSwitchResult.failure(TableSwitchError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(SwitchTableCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            ACTION,
            value(command.seatingId()),
            value(command.tableId()),
            value(command.tableGroupId()),
            normalize(command.actorType()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private TableSwitchResult execute(SwitchTableCommand command, StoreScope scope, IdempotencyRecord started) {
        validateStore(command.actorId(), command.actorType(), scope);

        Seating seating = seatingRepository.findById(scope, new SeatingId(command.seatingId()))
            .orElseThrow(() -> new ApplicationFailure(TableSwitchError.SEATING_NOT_FOUND));
        if (seating.status() != SeatingStatus.OCCUPIED) {
            throw new ApplicationFailure(TableSwitchError.SEATING_NOT_OCCUPIED);
        }

        SeatingResource sourceResource = seatingRepository.findActiveResourceBySeating(scope, seating.id())
            .orElseThrow(() -> new ApplicationFailure(TableSwitchError.ACTIVE_SEATING_RESOURCE_NOT_FOUND));
        require(seatingResourceValidator.validate(sourceResource.resourceType(), sourceResource.resourceId()), TableSwitchError.INVALID_COMMAND);
        require(cleaningResourceValidator.validate(sourceResource.resourceType(), sourceResource.resourceId()), TableSwitchError.INVALID_COMMAND);
        if (cleaningRepository.findActiveByResource(scope, sourceResource.resourceType(), sourceResource.resourceId()).isPresent()) {
            throw new ApplicationFailure(TableSwitchError.CLEANING_ALREADY_ACTIVE);
        }

        ResourceContext source = sourceForRelease(scope, sourceResource.resourceType(), sourceResource.resourceId());
        ResourceContext target = targetResource(command, scope, source);

        transitionTables(scope, source.tables(), DiningTableStatus.AVAILABLE);
        seatingRepository.saveResource(scope, new SeatingResource(
            sourceResource.id(),
            scope,
            seating.id(),
            source.resourceType(),
            source.resourceId(),
            "released"
        ));
        SeatingResource targetSeatingResource = seatingRepository.saveResource(
            scope,
            new SeatingResource(UUID.randomUUID(), scope, seating.id(), target.resourceType(), target.resourceId(), "active")
        );
        transitionTables(scope, target.tables(), DiningTableStatus.OCCUPIED);

        String metadata = metadata(seating, source, target, null, command.reasonCode(), command.note(), started.idempotencyKey());
        List<UUID> eventIds = appendBusinessEvents(scope, command, source, target, metadata);
        List<UUID> transitionIds = appendTransitionLogs(scope, command, sourceResource, targetSeatingResource, source, target, metadata);
        AuditLog auditLog = appendCompletedAudit(scope, command, seating, metadata);

        IdempotencyRecord completed = completeIdempotency(scope, started, seating, source, target);
        return TableSwitchResult.success(
            seating.id().value(),
            source.resourceType(),
            source.resourceId(),
            DiningTableStatus.AVAILABLE.code(),
            target.resourceType(),
            target.resourceId(),
            DiningTableStatus.OCCUPIED.code(),
            null,
            seating.status().code(),
            completed.status().code(),
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private void validateStore(UUID actorId, String actorType, StoreScope scope) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(TableSwitchError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(TableSwitchError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, actorId, actorType), TableSwitchError.STORE_ACCESS_DENIED);
    }

    private ResourceContext sourceForRelease(StoreScope scope, String resourceType, UUID resourceId) {
        if (RESOURCE_TABLE.equals(resourceType)) {
            DiningTable table = diningTableRepository.findById(scope, new TableId(resourceId))
                .orElseThrow(() -> new ApplicationFailure(TableSwitchError.TABLE_NOT_FOUND));
            if (table.status() != DiningTableStatus.OCCUPIED) {
                throw new ApplicationFailure(TableSwitchError.TABLE_RESOURCE_UNAVAILABLE);
            }
            validateTableTransition(DiningTableStatus.OCCUPIED, DiningTableStatus.CLEANING);
            return new ResourceContext(resourceType, resourceId, List.of(table));
        }
        if (RESOURCE_GROUP.equals(resourceType)) {
            TableGroup group = tableGroupRepository.findById(scope, new TableGroupId(resourceId))
                .orElseThrow(() -> new ApplicationFailure(TableSwitchError.TABLE_GROUP_NOT_FOUND));
            List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, group.id());
            require(tableGroupValidationRule.evaluate(group, members), TableSwitchError.TABLE_GROUP_INVALID);
            List<DiningTable> tables = loadMemberTables(scope, members);
            tables.forEach(table -> {
                if (table.status() != DiningTableStatus.OCCUPIED) {
                    throw new ApplicationFailure(TableSwitchError.TABLE_RESOURCE_UNAVAILABLE);
                }
            });
            validateTableTransition(DiningTableStatus.OCCUPIED, DiningTableStatus.CLEANING);
            return new ResourceContext(resourceType, resourceId, tables);
        }
        throw new ApplicationFailure(TableSwitchError.INVALID_COMMAND);
    }

    private ResourceContext targetResource(SwitchTableCommand command, StoreScope scope, ResourceContext source) {
        if (command.tableId() != null) {
            if (RESOURCE_TABLE.equals(source.resourceType()) && source.resourceId().equals(command.tableId())) {
                throw new ApplicationFailure(TableSwitchError.TARGET_SAME_AS_CURRENT);
            }
            DiningTable table = diningTableRepository.findById(scope, new TableId(command.tableId()))
                .orElseThrow(() -> new ApplicationFailure(TableSwitchError.TABLE_NOT_FOUND));
            validateTargetTable(scope, table, TableSwitchError.TABLE_NOT_AVAILABLE);
            return new ResourceContext(RESOURCE_TABLE, table.id().value(), List.of(table));
        }

        if (RESOURCE_GROUP.equals(source.resourceType()) && source.resourceId().equals(command.tableGroupId())) {
            throw new ApplicationFailure(TableSwitchError.TARGET_SAME_AS_CURRENT);
        }
        TableGroup group = tableGroupRepository.findById(scope, new TableGroupId(command.tableGroupId()))
            .orElseThrow(() -> new ApplicationFailure(TableSwitchError.TABLE_GROUP_NOT_FOUND));
        require(tableAvailabilityRule.evaluate(group), TableSwitchError.TABLE_GROUP_INVALID);
        List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, group.id());
        require(tableGroupValidationRule.evaluate(group, members), TableSwitchError.TABLE_GROUP_INVALID);
        requireNoLock(scope, RESOURCE_GROUP, group.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_GROUP, group.id().value())) {
            throw new ApplicationFailure(TableSwitchError.TABLE_RESOURCE_UNAVAILABLE);
        }

        List<DiningTable> memberTables = new ArrayList<>();
        for (TableGroupMember member : members) {
            DiningTable table = diningTableRepository.findById(scope, member.tableId())
                .orElseThrow(() -> new ApplicationFailure(TableSwitchError.TABLE_GROUP_INVALID));
            validateTargetGroupMemberTable(scope, table);
            memberTables.add(table);
        }
        return new ResourceContext(RESOURCE_GROUP, group.id().value(), memberTables);
    }

    private void validateTargetTable(
        StoreScope scope,
        DiningTable table,
        TableSwitchError unavailableError
    ) {
        require(tableAvailabilityRule.evaluate(table), unavailableError);
        requireNoLock(scope, RESOURCE_TABLE, table.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_TABLE, table.id().value())) {
            throw new ApplicationFailure(unavailableError);
        }
        validateTableTransition(table.status(), DiningTableStatus.LOCKED);
        validateTableTransition(DiningTableStatus.LOCKED, DiningTableStatus.OCCUPIED);
    }

    private void validateTargetGroupMemberTable(StoreScope scope, DiningTable table) {
        require(tableAvailabilityRule.evaluate(table), TableSwitchError.TABLE_GROUP_INVALID);
        requireNoLock(scope, RESOURCE_TABLE, table.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_TABLE, table.id().value())) {
            throw new ApplicationFailure(TableSwitchError.TABLE_GROUP_INVALID);
        }
        validateTableTransition(table.status(), DiningTableStatus.LOCKED);
        validateTableTransition(DiningTableStatus.LOCKED, DiningTableStatus.OCCUPIED);
    }

    private void requireNoLock(StoreScope scope, String resourceType, UUID resourceId) {
        require(
            tableLockRule.evaluate(tableLockRepository.existsActiveConflict(scope, resourceType, resourceId, OffsetDateTime.now())),
            TableSwitchError.TABLE_LOCK_CONFLICT
        );
    }

    private List<DiningTable> loadMemberTables(StoreScope scope, List<TableGroupMember> members) {
        List<DiningTable> tables = new ArrayList<>();
        for (TableGroupMember member : members) {
            DiningTable table = diningTableRepository.findById(scope, member.tableId())
                .orElseThrow(() -> new ApplicationFailure(TableSwitchError.TABLE_NOT_FOUND));
            tables.add(table);
        }
        return tables;
    }

    private void transitionTables(StoreScope scope, List<DiningTable> tables, DiningTableStatus toStatus) {
        for (DiningTable table : tables) {
            diningTableRepository.save(
                scope,
                new DiningTable(
                    table.id(),
                    table.scope(),
                    table.areaId(),
                    table.tableCode(),
                    table.capacity(),
                    toStatus,
                    table.combinable()
                )
            );
        }
    }

    private void validateTableTransition(DiningTableStatus from, DiningTableStatus to) {
        TransitionResult<DiningTableStatus> transition = diningTableStateMachine.validateTransition(from, to);
        if (!transition.accepted()) {
            throw new ApplicationFailure(TableSwitchError.ILLEGAL_STATE_TRANSITION);
        }
    }

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        SwitchTableCommand command,
        ResourceContext source,
        ResourceContext target,
        String metadata
    ) {
        String sourceCategory = source(command.actorType());
        List<BusinessEvent> events = List.of(
            new BusinessEvent(UUID.randomUUID(), EVENT_SWITCH_COMPLETED, "seating", command.seatingId(), command.actorType(), command.actorId(), sourceCategory, metadata),
            new BusinessEvent(UUID.randomUUID(), EVENT_TABLE_AVAILABLE, source.resourceType(), source.resourceId(), command.actorType(), command.actorId(), sourceCategory, metadata),
            new BusinessEvent(UUID.randomUUID(), EVENT_TABLE_OCCUPIED, target.resourceType(), target.resourceId(), command.actorType(), command.actorId(), sourceCategory, metadata)
        );
        List<UUID> ids = new ArrayList<>();
        for (BusinessEvent event : events) {
            require(businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()), TableSwitchError.BUSINESS_EVENT_WRITE_FAILED);
            try {
                ids.add(businessEventRepository.append(scope, event).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(TableSwitchError.BUSINESS_EVENT_WRITE_FAILED);
            }
        }
        return ids;
    }

    private List<UUID> appendTransitionLogs(
        StoreScope scope,
        SwitchTableCommand command,
        SeatingResource sourceResource,
        SeatingResource targetResource,
        ResourceContext source,
        ResourceContext target,
        String metadata
    ) {
        List<StateTransitionLog> logs = new ArrayList<>();
        logs.add(newTransition("seating_resource", sourceResource.id(), "active", "released", "table.switch.release_source", command, metadata));
        logs.add(newTransition("seating_resource", targetResource.id(), "created", "active", "table.switch.assign_target", command, metadata));
        logs.add(newTransition(source.resourceType(), source.resourceId(), "occupied", "available", transitionCode(source.resourceType(), "available"), command, metadata));
        logs.add(newTransition(target.resourceType(), target.resourceId(), "available", "occupied", transitionCode(target.resourceType(), "occupy"), command, metadata));

        List<UUID> ids = new ArrayList<>();
        for (StateTransitionLog log : logs) {
            require(stateTransitionRule.evaluate(log.targetType(), log.targetId(), log.toStatus(), log.transitionCode(), log.actorType()), TableSwitchError.STATE_TRANSITION_WRITE_FAILED);
            try {
                ids.add(stateTransitionLogRepository.append(scope, log).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(TableSwitchError.STATE_TRANSITION_WRITE_FAILED);
            }
        }
        return ids;
    }

    private StateTransitionLog newTransition(
        String targetType,
        UUID targetId,
        String fromStatus,
        String toStatus,
        String transitionCode,
        SwitchTableCommand command,
        String metadata
    ) {
        return new StateTransitionLog(
            UUID.randomUUID(),
            targetType,
            targetId,
            fromStatus,
            toStatus,
            transitionCode,
            command.actorType(),
            command.actorId(),
            source(command.actorType()),
            metadata
        );
    }

    private AuditLog appendCompletedAudit(StoreScope scope, SwitchTableCommand command, Seating seating, String metadata) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_SWITCH_COMPLETED,
            "seating",
            seating.id().value(),
            source(command.actorType()),
            command.actorType(),
            command.actorId(),
            metadata
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), TableSwitchError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(TableSwitchError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(StoreScope scope, SwitchTableCommand command, IdempotencyKey idempotencyKey, TableSwitchError error) {
        try {
            String actorType = command == null ? "staff" : command.actorType();
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_SWITCH_FAILED,
                    "seating",
                    command == null ? null : command.seatingId(),
                    source(actorType),
                    actorType,
                    command == null ? null : command.actorId(),
                    """
                        {"failureReason":"%s","idempotencyKey":"%s"}
                        """.formatted(error.code(), escape(idempotencyKey.value())).trim()
                )
            );
        } catch (RuntimeException ignored) {
            // Preserve original application failure.
        }
    }

    private IdempotencyRecord completeIdempotency(
        StoreScope scope,
        IdempotencyRecord started,
        Seating seating,
        ResourceContext source,
        ResourceContext target
    ) {
        String snapshot = snapshot(
            seating.id().value(),
            source.resourceType(),
            source.resourceId(),
            DiningTableStatus.AVAILABLE.code(),
            target.resourceType(),
            target.resourceId(),
            DiningTableStatus.OCCUPIED.code(),
            null,
            seating.status().code()
        );
        IdempotencyRecord completionPayload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            "seating",
            seating.id().value(),
            snapshot
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, "seating");
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(TableSwitchError.REPOSITORY_SAVE_FAILED);
        }
    }

    private TableSwitchResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            TableSwitchError error = TableSwitchError.fromCode(decision.violationCode());
            if (error == TableSwitchError.COMMAND_IN_PROGRESS) {
                return TableSwitchResult.retryLater(error);
            }
            return TableSwitchResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return TableSwitchResult.failure(TableSwitchError.IDEMPOTENCY_CONFLICT);
            }
        }
        return TableSwitchResult.failure(TableSwitchError.IDEMPOTENCY_CONFLICT);
    }

    private TableSwitchResult replay(String snapshot) {
        return TableSwitchResult.replay(
            UUID.fromString(extract(snapshot, "seatingId")),
            extract(snapshot, "fromResourceType"),
            UUID.fromString(extract(snapshot, "fromResourceId")),
            extract(snapshot, "fromResourceStatus"),
            extract(snapshot, "toResourceType"),
            UUID.fromString(extract(snapshot, "toResourceId")),
            extract(snapshot, "toResourceStatus"),
            extractUuidOrNull(snapshot, "cleaningId"),
            extract(snapshot, "seatingStatus")
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, TableSwitchError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve original application failure.
        }
    }

    private static TableSwitchError validateCommand(SwitchTableCommand command) {
        if (
            command == null
                || command.tenantId() == null
                || command.storeId() == null
                || command.seatingId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
                || !hasText(command.idempotencyKey())
        ) {
            return TableSwitchError.INVALID_COMMAND;
        }
        if (command.tableId() != null && command.tableGroupId() != null) {
            return TableSwitchError.TARGET_AMBIGUOUS;
        }
        if (command.tableId() == null && command.tableGroupId() == null) {
            return TableSwitchError.TARGET_REQUIRED;
        }
        return null;
    }

    private static TableSwitchError fromRuleCode(String code, TableSwitchError fallback) {
        if ("table_resource_unavailable".equals(code) || "party_size_outside_capacity".equals(code)) {
            return fallback;
        }
        TableSwitchError error = TableSwitchError.fromCode(code);
        return error == TableSwitchError.INVALID_COMMAND ? fallback : error;
    }

    private static void require(RuleDecision decision, TableSwitchError fallback) {
        if (!decision.accepted()) {
            throw new ApplicationFailure(fromRuleCode(decision.violationCode(), fallback));
        }
    }

    private static String source(String actorType) {
        return OperationSource.fromActorType(actorType);
    }

    private static String transitionCode(String resourceType, String status) {
        if (RESOURCE_TABLE.equals(resourceType)) {
            return "dining_table." + status;
        }
        return "table_group." + status;
    }

    private static String metadata(
        Seating seating,
        ResourceContext source,
        ResourceContext target,
        UUID cleaningId,
        String reasonCode,
        String note,
        IdempotencyKey idempotencyKey
    ) {
        return """
            {"seatingId":"%s","fromResourceType":"%s","fromResourceId":"%s","toResourceType":"%s","toResourceId":"%s","cleaningId":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            seating.id().value(),
            source.resourceType(),
            source.resourceId(),
            target.resourceType(),
            target.resourceId(),
            jsonNullable(cleaningId),
            jsonNullable(reasonCode),
            jsonNullable(note),
            escape(idempotencyKey.value())
        ).trim();
    }

    private static String snapshot(
        UUID seatingId,
        String fromResourceType,
        UUID fromResourceId,
        String fromResourceStatus,
        String toResourceType,
        UUID toResourceId,
        String toResourceStatus,
        UUID cleaningId,
        String seatingStatus
    ) {
        return """
            {"seatingId":"%s","fromResourceType":"%s","fromResourceId":"%s","fromResourceStatus":"%s","toResourceType":"%s","toResourceId":"%s","toResourceStatus":"%s","cleaningId":%s,"seatingStatus":"%s"}
            """.formatted(
            seatingId,
            fromResourceType,
            fromResourceId,
            fromResourceStatus,
            toResourceType,
            toResourceId,
            toResourceStatus,
            jsonNullable(cleaningId),
            seatingStatus
        ).trim();
    }

    private static UUID extractUuidOrNull(String json, String key) {
        String value = extractNullable(json, key);
        return value == null ? null : UUID.fromString(value);
    }

    private static String extractNullable(String json, String key) {
        if (json == null) {
            throw new IllegalArgumentException("snapshot_missing");
        }
        Pattern nullValue = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*null");
        Matcher nullMatcher = nullValue.matcher(json);
        if (nullMatcher.find()) {
            return null;
        }
        return extract(json, key);
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

    private static String sha256(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha_256_unavailable", exception);
        }
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ResourceContext(String resourceType, UUID resourceId, List<DiningTable> tables) {

        private ResourceContext {
            tables = List.copyOf(tables);
        }
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final TableSwitchError error;
        private final boolean retryLater;

        private ApplicationFailure(TableSwitchError error) {
            this(error, false);
        }

        private ApplicationFailure(TableSwitchError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private TableSwitchError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
