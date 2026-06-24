package com.rpb.reservation.cleaning.application.service;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.audit.domain.StateTransitionLog;
import com.rpb.reservation.audit.rule.DefaultAuditRule;
import com.rpb.reservation.audit.rule.DefaultBusinessEventRule;
import com.rpb.reservation.audit.rule.DefaultStateTransitionRule;
import com.rpb.reservation.cleaning.application.CleaningApplicationError;
import com.rpb.reservation.cleaning.application.CleaningApplicationResult;
import com.rpb.reservation.cleaning.application.command.CompleteCleaningCommand;
import com.rpb.reservation.cleaning.application.command.StartCleaningCommand;
import com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort;
import com.rpb.reservation.cleaning.application.validator.CleaningResourceValidator;
import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.state.CleaningStateMachine;
import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.common.scope.DefaultStoreAccessPolicy;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.common.value.IdempotencyKey;
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
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.group.rule.DefaultTableGroupValidationRule;
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
public class CleaningApplicationService {

    private static final String ACTION_START = "start_cleaning";
    private static final String ACTION_COMPLETE = "complete_cleaning";
    private static final String RESOURCE_TABLE = "dining_table";
    private static final String RESOURCE_GROUP = "table_group";

    private final StoreRepositoryPort storeRepository;
    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final SeatingRepositoryPort seatingRepository;
    private final CleaningRepositoryPort cleaningRepository;
    private final BusinessEventRepositoryPort businessEventRepository;
    private final StateTransitionLogRepositoryPort stateTransitionLogRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final IdempotencyRepositoryPort idempotencyRepository;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();
    private final DefaultSeatingResourceValidator seatingResourceValidator = new DefaultSeatingResourceValidator();
    private final CleaningResourceValidator cleaningResourceValidator = new CleaningResourceValidator();
    private final DefaultTableGroupValidationRule tableGroupValidationRule = new DefaultTableGroupValidationRule();
    private final DefaultAuditRule auditRule = new DefaultAuditRule();
    private final DefaultBusinessEventRule businessEventRule = new DefaultBusinessEventRule();
    private final DefaultStateTransitionRule stateTransitionRule = new DefaultStateTransitionRule();
    private final DefaultIdempotencyRule idempotencyRule = new DefaultIdempotencyRule();
    private final DiningTableStateMachine diningTableStateMachine = new DiningTableStateMachine();
    private final CleaningStateMachine cleaningStateMachine = new CleaningStateMachine();

    public CleaningApplicationService(
        StoreRepositoryPort storeRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
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
        this.seatingRepository = seatingRepository;
        this.cleaningRepository = cleaningRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public CleaningApplicationResult startCleaning(StartCleaningCommand command) {
        CleaningApplicationError preValidationError = validateStartCommand(command);
        if (preValidationError != null) {
            return CleaningApplicationResult.failure(preValidationError);
        }

        StoreScope scope = new StoreScope(new TenantId(command.tenantId()), command.storeId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String requestHash = requestHash(command);

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByScopeActionKey(scope, command.actorType(), ACTION_START, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExistingIdempotency(existing.get(), requestHash);
        }

        IdempotencyRecord started;
        try {
            started = idempotencyRepository.start(scope, command.actorType(), ACTION_START, idempotencyKey, requestHash, OffsetDateTime.now().plusMinutes(30));
        } catch (RuntimeException exception) {
            return CleaningApplicationResult.failure(CleaningApplicationError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return executeStart(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command.actorId(), command.actorType(), command.reasonCode(), command.note(), started.idempotencyKey(), "cleaning.start.failed", failure.error());
            return failure.retryLater()
                ? CleaningApplicationResult.retryLater(failure.error())
                : CleaningApplicationResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, CleaningApplicationError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command.actorId(), command.actorType(), command.reasonCode(), command.note(), started.idempotencyKey(), "cleaning.start.failed", CleaningApplicationError.REPOSITORY_SAVE_FAILED);
            return CleaningApplicationResult.failure(CleaningApplicationError.REPOSITORY_SAVE_FAILED);
        }
    }

    @Transactional
    public CleaningApplicationResult completeCleaning(CompleteCleaningCommand command) {
        CleaningApplicationError preValidationError = validateCompleteCommand(command);
        if (preValidationError != null) {
            return CleaningApplicationResult.failure(preValidationError);
        }

        StoreScope scope = new StoreScope(new TenantId(command.tenantId()), command.storeId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String requestHash = requestHash(command);

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByScopeActionKey(scope, command.actorType(), ACTION_COMPLETE, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExistingIdempotency(existing.get(), requestHash);
        }

        IdempotencyRecord started;
        try {
            started = idempotencyRepository.start(scope, command.actorType(), ACTION_COMPLETE, idempotencyKey, requestHash, OffsetDateTime.now().plusMinutes(30));
        } catch (RuntimeException exception) {
            return CleaningApplicationResult.failure(CleaningApplicationError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return executeComplete(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command.actorId(), command.actorType(), command.reasonCode(), command.note(), started.idempotencyKey(), "cleaning.complete.failed", failure.error());
            return failure.retryLater()
                ? CleaningApplicationResult.retryLater(failure.error())
                : CleaningApplicationResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, CleaningApplicationError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command.actorId(), command.actorType(), command.reasonCode(), command.note(), started.idempotencyKey(), "cleaning.complete.failed", CleaningApplicationError.REPOSITORY_SAVE_FAILED);
            return CleaningApplicationResult.failure(CleaningApplicationError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(StartCleaningCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            ACTION_START,
            value(command.seatingId()),
            normalize(command.actorType()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    public static String requestHash(CompleteCleaningCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            ACTION_COMPLETE,
            value(command.cleaningId()),
            normalize(command.actorType()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private CleaningApplicationResult executeStart(StartCleaningCommand command, StoreScope scope, IdempotencyRecord started) {
        validateStore(command.actorId(), command.actorType(), scope);

        Seating seating = seatingRepository.findById(scope, new SeatingId(command.seatingId()))
            .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.SEATING_NOT_FOUND));
        if (seating.status() != SeatingStatus.OCCUPIED && seating.status() != SeatingStatus.COMPLETED) {
            throw new ApplicationFailure(CleaningApplicationError.TABLE_NOT_OCCUPIED);
        }

        SeatingResource seatingResource = seatingRepository.findActiveResourceBySeating(scope, seating.id())
            .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.SEATING_RESOURCE_NOT_FOUND));
        require(seatingResourceValidator.validate(seatingResource.resourceType(), seatingResource.resourceId()), CleaningApplicationError.RESOURCE_TARGET_INVALID);
        require(cleaningResourceValidator.validate(seatingResource.resourceType(), seatingResource.resourceId()), CleaningApplicationError.RESOURCE_TARGET_INVALID);

        if (cleaningRepository.findActiveByResource(scope, seatingResource.resourceType(), seatingResource.resourceId()).isPresent()) {
            throw new ApplicationFailure(CleaningApplicationError.CLEANING_ALREADY_ACTIVE);
        }

        ResourceContext resource = resourceForStart(scope, seatingResource.resourceType(), seatingResource.resourceId());
        UUID cleaningUuid = UUID.randomUUID();
        Cleaning cleaning = cleaningRepository.save(
            scope,
            new Cleaning(
                new CleaningId(cleaningUuid),
                scope,
                seating.id(),
                seatingResource.resourceType(),
                seatingResource.resourceId(),
                CleaningStatus.CLEANING
            )
        );

        transitionTables(scope, resource.tables(), DiningTableStatus.CLEANING);
        seatingRepository.saveResource(scope, new SeatingResource(
            seatingResource.id(),
            scope,
            seating.id(),
            seatingResource.resourceType(),
            seatingResource.resourceId(),
            "released"
        ));
        seatingRepository.save(scope, new Seating(
            seating.id(),
            scope,
            seating.sourceType(),
            seating.sourceId(),
            seating.seatingCode(),
            seating.manualOverrideReasonCode(),
            seating.note(),
            seating.partySizeSnapshot(),
            SeatingStatus.CLEANING_TRIGGERED,
            seating.completedAt()
        ));

        List<UUID> eventIds = appendBusinessEvents(
            scope,
            command.actorId(),
            command.actorType(),
            started.idempotencyKey(),
            List.of(
                new EventSpec("cleaning.started", "cleaning", cleaning.id().value(), metadata(cleaning, resource, command.reasonCode(), command.note())),
                new EventSpec("table.cleaning", resource.resourceType(), resource.resourceId(), metadata(cleaning, resource, command.reasonCode(), command.note()))
            )
        );
        List<UUID> transitionIds = appendTransitionLogs(
            scope,
            command.actorId(),
            command.actorType(),
            started.idempotencyKey(),
            List.of(
                new TransitionSpec("cleaning", cleaning.id().value(), "pending", "cleaning", "cleaning.start", metadata(cleaning, resource, command.reasonCode(), command.note())),
                new TransitionSpec(resource.resourceType(), resource.resourceId(), "occupied", "cleaning", transitionCode(resource.resourceType(), "cleaning"), metadata(cleaning, resource, command.reasonCode(), command.note()))
            )
        );
        AuditLog auditLog = appendCompletedAudit(
            scope,
            "cleaning.start.completed",
            cleaning.id().value(),
            command.actorId(),
            command.actorType(),
            started.idempotencyKey(),
            metadata(cleaning, resource, command.reasonCode(), command.note())
        );

        IdempotencyRecord completed = completeIdempotency(scope, started, cleaning, resource, DiningTableStatus.CLEANING.code());
        return CleaningApplicationResult.success(
            cleaning.id().value(),
            seating.id().value(),
            resource.resourceType(),
            resource.resourceId(),
            DiningTableStatus.OCCUPIED.code(),
            DiningTableStatus.CLEANING.code(),
            cleaning.status().code(),
            completed.status().code(),
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private CleaningApplicationResult executeComplete(CompleteCleaningCommand command, StoreScope scope, IdempotencyRecord started) {
        validateStore(command.actorId(), command.actorType(), scope);

        Cleaning existingCleaning = cleaningRepository.findById(scope, new CleaningId(command.cleaningId()))
            .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.CLEANING_NOT_FOUND));
        if (existingCleaning.status() == CleaningStatus.COMPLETED || existingCleaning.status() == CleaningStatus.RELEASED) {
            throw new ApplicationFailure(CleaningApplicationError.CLEANING_ALREADY_COMPLETED);
        }
        if (existingCleaning.status() != CleaningStatus.CLEANING) {
            throw new ApplicationFailure(CleaningApplicationError.ILLEGAL_STATE_TRANSITION);
        }
        require(cleaningResourceValidator.validate(existingCleaning.resourceType(), existingCleaning.resourceId()), CleaningApplicationError.RESOURCE_TARGET_INVALID);

        ResourceContext resource = resourceForComplete(scope, existingCleaning.resourceType(), existingCleaning.resourceId());
        validateCleaningTransition(CleaningStatus.CLEANING, CleaningStatus.COMPLETED);
        validateCleaningTransition(CleaningStatus.COMPLETED, CleaningStatus.RELEASED);

        transitionTables(scope, resource.tables(), DiningTableStatus.AVAILABLE);
        Cleaning released = cleaningRepository.save(
            scope,
            new Cleaning(
                existingCleaning.id(),
                scope,
                existingCleaning.seatingId(),
                existingCleaning.resourceType(),
                existingCleaning.resourceId(),
                CleaningStatus.RELEASED
            )
        );

        List<UUID> eventIds = appendBusinessEvents(
            scope,
            command.actorId(),
            command.actorType(),
            started.idempotencyKey(),
            List.of(
                new EventSpec("cleaning.completed", "cleaning", released.id().value(), metadata(released, resource, command.reasonCode(), command.note())),
                new EventSpec("table.available", resource.resourceType(), resource.resourceId(), metadata(released, resource, command.reasonCode(), command.note()))
            )
        );
        List<UUID> transitionIds = appendTransitionLogs(
            scope,
            command.actorId(),
            command.actorType(),
            started.idempotencyKey(),
            List.of(
                new TransitionSpec("cleaning", released.id().value(), "cleaning", "released", "cleaning.complete", metadata(released, resource, command.reasonCode(), command.note())),
                new TransitionSpec(resource.resourceType(), resource.resourceId(), "cleaning", "available", transitionCode(resource.resourceType(), "available"), metadata(released, resource, command.reasonCode(), command.note()))
            )
        );
        AuditLog auditLog = appendCompletedAudit(
            scope,
            "cleaning.complete.completed",
            released.id().value(),
            command.actorId(),
            command.actorType(),
            started.idempotencyKey(),
            metadata(released, resource, command.reasonCode(), command.note())
        );

        IdempotencyRecord completed = completeIdempotency(scope, started, released, resource, DiningTableStatus.AVAILABLE.code());
        return CleaningApplicationResult.success(
            released.id().value(),
            released.seatingId().value(),
            resource.resourceType(),
            resource.resourceId(),
            DiningTableStatus.CLEANING.code(),
            DiningTableStatus.AVAILABLE.code(),
            released.status().code(),
            completed.status().code(),
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private void validateStore(UUID actorId, String actorType, StoreScope scope) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(CleaningApplicationError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, actorId, actorType), CleaningApplicationError.STORE_ACCESS_DENIED);
    }

    private ResourceContext resourceForStart(StoreScope scope, String resourceType, UUID resourceId) {
        if (RESOURCE_TABLE.equals(resourceType)) {
            DiningTable table = diningTableRepository.findById(scope, new TableId(resourceId))
                .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.TABLE_NOT_FOUND));
            validateTableStatus(table, DiningTableStatus.OCCUPIED, CleaningApplicationError.TABLE_NOT_OCCUPIED);
            validateTableTransition(DiningTableStatus.OCCUPIED, DiningTableStatus.CLEANING);
            return new ResourceContext(resourceType, resourceId, List.of(table));
        }
        if (RESOURCE_GROUP.equals(resourceType)) {
            TableGroup group = tableGroupRepository.findById(scope, new TableGroupId(resourceId))
                .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.INVALID_TABLE_GROUP));
            List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, group.id());
            require(tableGroupValidationRule.evaluate(group, members), CleaningApplicationError.INVALID_TABLE_GROUP);
            List<DiningTable> tables = loadMemberTables(scope, members);
            tables.forEach(table -> validateTableStatus(table, DiningTableStatus.OCCUPIED, CleaningApplicationError.TABLE_NOT_OCCUPIED));
            validateTableTransition(DiningTableStatus.OCCUPIED, DiningTableStatus.CLEANING);
            return new ResourceContext(resourceType, resourceId, tables);
        }
        throw new ApplicationFailure(CleaningApplicationError.RESOURCE_TARGET_INVALID);
    }

    private ResourceContext resourceForComplete(StoreScope scope, String resourceType, UUID resourceId) {
        if (RESOURCE_TABLE.equals(resourceType)) {
            DiningTable table = diningTableRepository.findById(scope, new TableId(resourceId))
                .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.TABLE_NOT_FOUND));
            validateTableStatus(table, DiningTableStatus.CLEANING, CleaningApplicationError.TABLE_NOT_CLEANING);
            validateTableTransition(DiningTableStatus.CLEANING, DiningTableStatus.AVAILABLE);
            return new ResourceContext(resourceType, resourceId, List.of(table));
        }
        if (RESOURCE_GROUP.equals(resourceType)) {
            TableGroup group = tableGroupRepository.findById(scope, new TableGroupId(resourceId))
                .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.INVALID_TABLE_GROUP));
            List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, group.id());
            require(tableGroupValidationRule.evaluate(group, members), CleaningApplicationError.INVALID_TABLE_GROUP);
            List<DiningTable> tables = loadMemberTables(scope, members);
            tables.forEach(table -> validateTableStatus(table, DiningTableStatus.CLEANING, CleaningApplicationError.TABLE_NOT_CLEANING));
            validateTableTransition(DiningTableStatus.CLEANING, DiningTableStatus.AVAILABLE);
            return new ResourceContext(resourceType, resourceId, tables);
        }
        throw new ApplicationFailure(CleaningApplicationError.RESOURCE_TARGET_INVALID);
    }

    private List<DiningTable> loadMemberTables(StoreScope scope, List<TableGroupMember> members) {
        List<DiningTable> tables = new ArrayList<>();
        for (TableGroupMember member : members) {
            DiningTable table = diningTableRepository.findById(scope, member.tableId())
                .orElseThrow(() -> new ApplicationFailure(CleaningApplicationError.TABLE_NOT_FOUND));
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

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        UUID actorId,
        String actorType,
        IdempotencyKey idempotencyKey,
        List<EventSpec> events
    ) {
        List<UUID> ids = new ArrayList<>();
        for (EventSpec spec : events) {
            BusinessEvent event = new BusinessEvent(
                UUID.randomUUID(),
                spec.eventType(),
                spec.targetType(),
                spec.targetId(),
                actorType,
                actorId,
                actorType,
                enrichMetadata(spec.metadata(), idempotencyKey)
            );
            require(businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()), CleaningApplicationError.BUSINESS_EVENT_WRITE_FAILED);
            try {
                ids.add(businessEventRepository.append(scope, event).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(CleaningApplicationError.BUSINESS_EVENT_WRITE_FAILED);
            }
        }
        return ids;
    }

    private List<UUID> appendTransitionLogs(
        StoreScope scope,
        UUID actorId,
        String actorType,
        IdempotencyKey idempotencyKey,
        List<TransitionSpec> transitions
    ) {
        List<UUID> ids = new ArrayList<>();
        for (TransitionSpec spec : transitions) {
            StateTransitionLog log = new StateTransitionLog(
                UUID.randomUUID(),
                spec.targetType(),
                spec.targetId(),
                spec.fromStatus(),
                spec.toStatus(),
                spec.transitionCode(),
                actorType,
                actorId,
                actorType,
                enrichMetadata(spec.metadata(), idempotencyKey)
            );
            require(stateTransitionRule.evaluate(log.targetType(), log.targetId(), log.toStatus(), log.transitionCode(), log.actorType()), CleaningApplicationError.STATE_TRANSITION_WRITE_FAILED);
            try {
                ids.add(stateTransitionLogRepository.append(scope, log).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(CleaningApplicationError.STATE_TRANSITION_WRITE_FAILED);
            }
        }
        return ids;
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        String operationCode,
        UUID cleaningId,
        UUID actorId,
        String actorType,
        IdempotencyKey idempotencyKey,
        String metadata
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            operationCode,
            "cleaning",
            cleaningId,
            actorType,
            actorType,
            actorId,
            enrichMetadata(metadata, idempotencyKey)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), CleaningApplicationError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(CleaningApplicationError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        UUID actorId,
        String actorType,
        String reasonCode,
        String note,
        IdempotencyKey idempotencyKey,
        String operationCode,
        CleaningApplicationError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    operationCode,
                    "cleaning",
                    null,
                    actorType,
                    actorType,
                    actorId,
                    """
                        {"failureReason":"%s","reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
                        """.formatted(error.code(), jsonNullable(reasonCode), jsonNullable(note), idempotencyKey.value()).trim()
                )
            );
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private IdempotencyRecord completeIdempotency(
        StoreScope scope,
        IdempotencyRecord started,
        Cleaning cleaning,
        ResourceContext resource,
        String currentTableStatus
    ) {
        String snapshot = snapshot(
            cleaning.id().value(),
            cleaning.seatingId().value(),
            resource.resourceType(),
            resource.resourceId(),
            currentTableStatus,
            cleaning.status().code()
        );
        IdempotencyRecord completionPayload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            "cleaning",
            cleaning.id().value(),
            snapshot
        );
        return idempotencyRepository.complete(scope, completionPayload, "cleaning");
    }

    private CleaningApplicationResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            CleaningApplicationError error = CleaningApplicationError.fromCode(decision.violationCode());
            if (error == CleaningApplicationError.COMMAND_IN_PROGRESS) {
                return CleaningApplicationResult.retryLater(error);
            }
            return CleaningApplicationResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            return replay(existing.responseSnapshot());
        }
        return CleaningApplicationResult.failure(CleaningApplicationError.IDEMPOTENCY_CONFLICT);
    }

    private CleaningApplicationResult replay(String snapshot) {
        return CleaningApplicationResult.replay(
            UUID.fromString(extract(snapshot, "cleaningId")),
            UUID.fromString(extract(snapshot, "seatingId")),
            extract(snapshot, "resourceType"),
            UUID.fromString(extract(snapshot, "resourceId")),
            extract(snapshot, "currentTableStatus"),
            extract(snapshot, "cleaningStatus")
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, CleaningApplicationError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private void validateTableStatus(DiningTable table, DiningTableStatus expectedStatus, CleaningApplicationError error) {
        if (table.status() != expectedStatus) {
            throw new ApplicationFailure(error);
        }
    }

    private void validateTableTransition(DiningTableStatus from, DiningTableStatus to) {
        TransitionResult<DiningTableStatus> transition = diningTableStateMachine.validateTransition(from, to);
        if (!transition.accepted()) {
            throw new ApplicationFailure(CleaningApplicationError.ILLEGAL_STATE_TRANSITION);
        }
    }

    private void validateCleaningTransition(CleaningStatus from, CleaningStatus to) {
        TransitionResult<CleaningStatus> transition = cleaningStateMachine.validateTransition(from, to);
        if (!transition.accepted()) {
            throw new ApplicationFailure(CleaningApplicationError.ILLEGAL_STATE_TRANSITION);
        }
    }

    private static CleaningApplicationError validateStartCommand(StartCleaningCommand command) {
        if (
            command == null
                || command.tenantId() == null
                || command.storeId() == null
                || command.seatingId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
                || !hasText(command.idempotencyKey())
        ) {
            return CleaningApplicationError.INVALID_COMMAND;
        }
        return null;
    }

    private static CleaningApplicationError validateCompleteCommand(CompleteCleaningCommand command) {
        if (
            command == null
                || command.tenantId() == null
                || command.storeId() == null
                || command.cleaningId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
                || !hasText(command.idempotencyKey())
        ) {
            return CleaningApplicationError.INVALID_COMMAND;
        }
        return null;
    }

    private static void require(RuleDecision decision, CleaningApplicationError fallback) {
        if (!decision.accepted()) {
            CleaningApplicationError error = CleaningApplicationError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == CleaningApplicationError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String transitionCode(String resourceType, String status) {
        return RESOURCE_TABLE.equals(resourceType) ? "dining_table." + status : "table_group." + status;
    }

    private static String metadata(Cleaning cleaning, ResourceContext resource, String reasonCode, String note) {
        return """
            {"cleaningId":"%s","seatingId":"%s","resourceType":"%s","resourceId":"%s","reasonCode":%s,"note":%s}
            """.formatted(
            cleaning.id().value(),
            cleaning.seatingId().value(),
            resource.resourceType(),
            resource.resourceId(),
            jsonNullable(reasonCode),
            jsonNullable(note)
        ).trim();
    }

    private static String enrichMetadata(String json, IdempotencyKey idempotencyKey) {
        String base = json == null || json.isBlank() ? "{}" : json.trim();
        if (!base.endsWith("}")) {
            return base;
        }
        String prefix = base.substring(0, base.length() - 1);
        String separator = prefix.endsWith("{") ? "" : ",";
        return prefix + separator + "\"idempotencyKey\":\"" + escape(idempotencyKey.value()) + "\"}";
    }

    private static String snapshot(
        UUID cleaningId,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        String currentTableStatus,
        String cleaningStatus
    ) {
        return """
            {"cleaningId":"%s","seatingId":"%s","resourceType":"%s","resourceId":"%s","currentTableStatus":"%s","cleaningStatus":"%s"}
            """.formatted(cleaningId, seatingId, resourceType, resourceId, currentTableStatus, cleaningStatus).trim();
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

    private record EventSpec(String eventType, String targetType, UUID targetId, String metadata) {
    }

    private record TransitionSpec(String targetType, UUID targetId, String fromStatus, String toStatus, String transitionCode, String metadata) {
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final CleaningApplicationError error;
        private final boolean retryLater;

        private ApplicationFailure(CleaningApplicationError error) {
            this(error, false);
        }

        private ApplicationFailure(CleaningApplicationError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private CleaningApplicationError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
