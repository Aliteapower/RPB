package com.rpb.reservation.walkin.application.service;

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
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.rule.DefaultCustomerIdentityRule;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort;
import com.rpb.reservation.seating.domain.Seating;
import com.rpb.reservation.seating.domain.SeatingResource;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.validator.DefaultSeatingResourceValidator;
import com.rpb.reservation.seating.validator.DefaultSeatingSourceValidator;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.application.TemporaryTableGroupError;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import com.rpb.reservation.table.application.service.TemporaryTableGroupApplicationService;
import com.rpb.reservation.table.application.service.TemporaryTableGroupCommand;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.domain.TableLock;
import com.rpb.reservation.table.group.rule.DefaultTableGroupValidationRule;
import com.rpb.reservation.table.rule.DefaultTableAssignmentRule;
import com.rpb.reservation.table.rule.DefaultTableAvailabilityRule;
import com.rpb.reservation.table.rule.DefaultTableCapacityRule;
import com.rpb.reservation.table.rule.DefaultTableLockRule;
import com.rpb.reservation.table.state.DiningTableStateMachine;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableLockStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.application.WalkInDirectSeatingError;
import com.rpb.reservation.walkin.application.WalkInDirectSeatingResult;
import com.rpb.reservation.walkin.application.command.SeatWalkInDirectlyCommand;
import com.rpb.reservation.walkin.application.port.out.WalkInRepositoryPort;
import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.value.WalkInId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
public class WalkInDirectSeatingApplicationService {

    private static final String ACTION = "seat_walk_in_directly";
    private static final String WALK_IN_SOURCE = "walk_in";
    private static final String RESOURCE_TABLE = "dining_table";
    private static final String RESOURCE_GROUP = "table_group";

    private final StoreRepositoryPort storeRepository;
    private final CustomerRepositoryPort customerRepository;
    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final TableLockRepositoryPort tableLockRepository;
    private final ReservationPreassignmentRepositoryPort preassignmentRepository;
    private final WalkInRepositoryPort walkInRepository;
    private final SeatingRepositoryPort seatingRepository;
    private final BusinessEventRepositoryPort businessEventRepository;
    private final StateTransitionLogRepositoryPort stateTransitionLogRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final IdempotencyRepositoryPort idempotencyRepository;
    private final TemporaryTableGroupApplicationService temporaryTableGroupService;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();
    private final DefaultCustomerIdentityRule customerIdentityRule = new DefaultCustomerIdentityRule();
    private final DefaultTableAvailabilityRule tableAvailabilityRule = new DefaultTableAvailabilityRule();
    private final DefaultTableCapacityRule tableCapacityRule = new DefaultTableCapacityRule();
    private final DefaultTableLockRule tableLockRule = new DefaultTableLockRule();
    private final DefaultTableAssignmentRule tableAssignmentRule = new DefaultTableAssignmentRule();
    private final DefaultTableGroupValidationRule tableGroupValidationRule = new DefaultTableGroupValidationRule();
    private final DefaultSeatingSourceValidator seatingSourceValidator = new DefaultSeatingSourceValidator();
    private final DefaultSeatingResourceValidator seatingResourceValidator = new DefaultSeatingResourceValidator();
    private final DefaultAuditRule auditRule = new DefaultAuditRule();
    private final DefaultBusinessEventRule businessEventRule = new DefaultBusinessEventRule();
    private final DefaultStateTransitionRule stateTransitionRule = new DefaultStateTransitionRule();
    private final DefaultIdempotencyRule idempotencyRule = new DefaultIdempotencyRule();
    private final DiningTableStateMachine diningTableStateMachine = new DiningTableStateMachine();

    public WalkInDirectSeatingApplicationService(
        StoreRepositoryPort storeRepository,
        CustomerRepositoryPort customerRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        TableLockRepositoryPort tableLockRepository,
        WalkInRepositoryPort walkInRepository,
        SeatingRepositoryPort seatingRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this(
            storeRepository,
            customerRepository,
            diningTableRepository,
            tableGroupRepository,
            tableLockRepository,
            NoopReservationPreassignmentRepository.INSTANCE,
            walkInRepository,
            seatingRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository
        );
    }

    @Autowired
    public WalkInDirectSeatingApplicationService(
        StoreRepositoryPort storeRepository,
        CustomerRepositoryPort customerRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        TableLockRepositoryPort tableLockRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        WalkInRepositoryPort walkInRepository,
        SeatingRepositoryPort seatingRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this.storeRepository = storeRepository;
        this.customerRepository = customerRepository;
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
        this.tableLockRepository = tableLockRepository;
        this.preassignmentRepository = preassignmentRepository;
        this.walkInRepository = walkInRepository;
        this.seatingRepository = seatingRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.temporaryTableGroupService = new TemporaryTableGroupApplicationService(
            diningTableRepository,
            tableGroupRepository,
            tableLockRepository,
            preassignmentRepository,
            seatingRepository,
            Clock.systemUTC()
        );
    }

    @Transactional
    public WalkInDirectSeatingResult seatWalkInDirectly(SeatWalkInDirectlyCommand command) {
        WalkInDirectSeatingError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return WalkInDirectSeatingResult.failure(preValidationError);
        }

        StoreScope scope = new StoreScope(new TenantId(command.tenantId()), command.storeId());
        IdempotencyKey idempotencyKey = new IdempotencyKey(command.idempotencyKey());
        String requestHash = requestHash(command);

        Optional<IdempotencyRecord> existing = idempotencyRepository.findByScopeActionKey(
            scope,
            command.actorType(),
            ACTION,
            idempotencyKey
        );
        if (existing.isPresent()) {
            return resolveExistingIdempotency(existing.get(), requestHash);
        }

        IdempotencyRecord started;
        try {
            started = idempotencyRepository.start(scope, command.actorType(), ACTION, idempotencyKey, requestHash, OffsetDateTime.now().plusMinutes(30));
        } catch (RuntimeException exception) {
            return WalkInDirectSeatingResult.failure(WalkInDirectSeatingError.REPOSITORY_SAVE_FAILED);
        }

        try {
            ExecutionResult execution = execute(command, scope, started);
            return execution.result();
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, failure.error());
            return failure.retryLater()
                ? WalkInDirectSeatingResult.retryLater(failure.error())
                : WalkInDirectSeatingResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, WalkInDirectSeatingError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, WalkInDirectSeatingError.REPOSITORY_SAVE_FAILED);
            return WalkInDirectSeatingResult.failure(WalkInDirectSeatingError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(SeatWalkInDirectlyCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.partySize()),
            value(command.customerId()),
            normalize(command.customerName()),
            normalize(command.customerNickname()),
            normalize(command.phoneE164()),
            value(command.tableId()),
            value(command.tableGroupId()),
            values(command.temporaryTableIds()),
            normalize(command.actorType()),
            normalize(command.overrideReasonCode()),
            normalize(command.overrideNote())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha_256_unavailable", exception);
        }
    }

    private ExecutionResult execute(SeatWalkInDirectlyCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope).orElseThrow(() -> new ApplicationFailure(WalkInDirectSeatingError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(WalkInDirectSeatingError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), WalkInDirectSeatingError.STORE_ACCESS_DENIED);

        StorePolicy policy = storeRepository.findCurrentPolicy(scope, OffsetDateTime.now())
            .orElseGet(() -> new StorePolicy(UUID.randomUUID(), scope, 15, 3, 90, "same_group_tail", "default"));
        BusinessDate businessDate = businessDate(store);
        TenantScope tenantScope = scope.tenantScope();
        PartySize partySize = new PartySize(command.partySize());
        Customer customer = resolveCustomer(command, tenantScope);
        ResourceSelection selection = resolveResource(command, scope, partySize, businessDate);

        require(seatingResourceValidator.validate(selection.resourceType(), selection.resourceId()), WalkInDirectSeatingError.INVALID_SEATING_RESOURCE);
        require(tableLockRule.evaluate(tableLockRepository.existsActiveConflict(scope, selection.resourceType(), selection.resourceId(), OffsetDateTime.now())), WalkInDirectSeatingError.TABLE_LOCK_CONFLICT);
        if (seatingRepository.existsActiveResourceOccupancy(scope, selection.resourceType(), selection.resourceId())) {
            throw new ApplicationFailure(WalkInDirectSeatingError.TABLE_RESOURCE_UNAVAILABLE);
        }

        UUID walkInUuid = UUID.randomUUID();
        UUID seatingUuid = UUID.randomUUID();
        require(seatingSourceValidator.validate(WALK_IN_SOURCE, walkInUuid), WalkInDirectSeatingError.INVALID_SEATING_SOURCE);

        OffsetDateTime now = OffsetDateTime.now();
        Instant lockedAt = now.toInstant();
        Instant lockedUntilAt = now.plusMinutes(policy.queueCallHoldMinutes()).toInstant();
        TableLock lock = tableLockRepository.save(
            scope,
            new TableLock(
                UUID.randomUUID(),
                scope,
                selection.resourceType(),
                selection.resourceId(),
                "walk-in-direct-" + walkInUuid,
                command.actorType(),
                WALK_IN_SOURCE,
                walkInUuid,
                lockedAt,
                lockedUntilAt,
                started.idempotencyKey(),
                TableLockStatus.ACTIVE
            )
        );

        WalkIn walkIn = walkInRepository.save(scope, new WalkIn(new WalkInId(walkInUuid), scope, partySize, "seated"));
        Seating seating = seatingRepository.save(
            scope,
            new Seating(
                new SeatingId(seatingUuid),
                scope,
                WALK_IN_SOURCE,
                walkIn.id().value(),
                "S-" + seatingUuid.toString().substring(0, 8),
                blankToNull(command.overrideReasonCode()),
                blankToNull(command.overrideNote()),
                partySize,
                SeatingStatus.OCCUPIED
            )
        );
        SeatingResource seatingResource = seatingRepository.saveResource(
            scope,
            new SeatingResource(UUID.randomUUID(), scope, seating.id(), selection.resourceType(), selection.resourceId(), "active")
        );

        List<DiningTable> occupiedTables = occupyTables(scope, selection);
        DiningTable occupiedTable = occupiedTables.size() == 1 ? occupiedTables.getFirst() : null;

        List<UUID> eventIds = appendBusinessEvents(scope, command, walkIn, seating, lock, selection);
        List<UUID> transitionIds = appendTransitionLogs(scope, command, walkIn, seating, selection, occupiedTable);
        AuditLog auditLog = appendCompletedAudit(scope, command, seating, customer, selection);

        String snapshot = snapshot(walkIn.id().value(), seating.id().value(), selection.resourceType(), selection.resourceId(), partySize.value());
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
        IdempotencyRecord completed = idempotencyRepository.complete(scope, completionPayload, "seating");

        return new ExecutionResult(WalkInDirectSeatingResult.success(
            walkIn.id().value(),
            seating.id().value(),
            seatingResource.resourceType(),
            seatingResource.resourceId(),
            seating.partySizeSnapshot().value(),
            walkIn.status(),
            seating.status().code(),
            seatingResource.status(),
            completed.status().code(),
            eventIds,
            transitionIds,
            auditLog.id()
        ));
    }

    private Customer resolveCustomer(SeatWalkInDirectlyCommand command, TenantScope tenantScope) {
        E164Phone phone = parsePhone(command.phoneE164());
        require(customerIdentityRule.evaluate(command.customerId(), command.customerName(), command.customerNickname(), phone), WalkInDirectSeatingError.INVALID_CUSTOMER_IDENTITY);
        if (command.customerId() != null) {
            Customer customer = customerRepository.findById(tenantScope, new CustomerId(command.customerId()))
                .orElseThrow(() -> new ApplicationFailure(WalkInDirectSeatingError.INVALID_CUSTOMER_IDENTITY));
            return refreshCustomerProfile(tenantScope, customer, command, phone);
        }
        if (phone.isPresent()) {
            Optional<Customer> existing = customerRepository.findByPhone(tenantScope, phone);
            if (existing.isPresent()) {
                return refreshCustomerProfile(tenantScope, existing.get(), command, phone);
            }
        }
        return customerRepository.save(
            tenantScope,
            new Customer(
                new CustomerId(UUID.randomUUID()),
                tenantScope,
                "C-" + UUID.randomUUID().toString().substring(0, 8),
                "walk_in_guest",
                phone,
                "active",
                blankToNull(command.customerName()),
                blankToNull(command.customerNickname())
            )
        );
    }

    private Customer refreshCustomerProfile(
        TenantScope tenantScope,
        Customer customer,
        SeatWalkInDirectlyCommand command,
        E164Phone phone
    ) {
        Customer refreshed = customer.refreshProfile(
            phone,
            blankToNull(command.customerName()),
            blankToNull(command.customerNickname())
        );
        if (refreshed.equals(customer)) {
            return customer;
        }
        return customerRepository.save(tenantScope, refreshed);
    }

    private ResourceSelection resolveResource(SeatWalkInDirectlyCommand command, StoreScope scope, PartySize partySize, BusinessDate businessDate) {
        List<DiningTable> tableCandidates = diningTableRepository.findCandidates(scope, partySize, businessDate);
        List<TableGroup> groupCandidates = tableGroupRepository.findCandidates(scope, partySize, businessDate);

        if (!command.temporaryTableIds().isEmpty()) {
            TemporaryTableGroupResult result = temporaryTableGroupService.createForSeating(new TemporaryTableGroupCommand(
                scope,
                command.temporaryTableIds(),
                partySize,
                businessDate,
                null
            ));
            if (!result.success()) {
                throw new ApplicationFailure(temporaryTableGroupError(result.error()));
            }
            return new ResourceSelection(RESOURCE_GROUP, result.group().id().value(), null, result.group(), result.memberTables());
        }

        if (command.tableId() != null) {
            DiningTable selected = diningTableRepository.findById(scope, new TableId(command.tableId()))
                .orElseThrow(() -> new ApplicationFailure(WalkInDirectSeatingError.TABLE_RESOURCE_UNAVAILABLE));
            validateTable(selected, partySize);
            return new ResourceSelection(RESOURCE_TABLE, selected.id().value(), selected, null, List.of());
        }

        if (command.tableGroupId() != null) {
            TableGroup selected = tableGroupRepository.findById(scope, new TableGroupId(command.tableGroupId()))
                .orElseThrow(() -> new ApplicationFailure(WalkInDirectSeatingError.INVALID_TABLE_GROUP));
            List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, selected.id());
            validateGroup(selected, members, partySize);
            return new ResourceSelection(RESOURCE_GROUP, selected.id().value(), null, selected, loadMemberTables(scope, members));
        }

        require(tableAssignmentRule.evaluate(tableCandidates, groupCandidates), WalkInDirectSeatingError.NO_ASSIGNABLE_TABLE);
        ApplicationFailure firstFailure = null;
        for (DiningTable table : tableCandidates) {
            try {
                validateTable(table, partySize);
                return new ResourceSelection(RESOURCE_TABLE, table.id().value(), table, null, List.of());
            } catch (ApplicationFailure failure) {
                firstFailure = firstFailure == null ? failure : firstFailure;
            }
        }
        for (TableGroup group : groupCandidates) {
            try {
                List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, group.id());
                validateGroup(group, members, partySize);
                return new ResourceSelection(RESOURCE_GROUP, group.id().value(), null, group, loadMemberTables(scope, members));
            } catch (ApplicationFailure failure) {
                firstFailure = firstFailure == null ? failure : firstFailure;
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        throw new ApplicationFailure(WalkInDirectSeatingError.NO_ASSIGNABLE_TABLE);
    }

    private static WalkInDirectSeatingError temporaryTableGroupError(TemporaryTableGroupError error) {
        return switch (error) {
            case MEMBER_REQUIRED -> WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED;
            case MEMBER_DUPLICATE -> WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE;
            case MEMBER_UNAVAILABLE -> WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE;
            case CAPACITY_INSUFFICIENT -> WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT;
            case LOCK_CONFLICT -> WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_LOCK_CONFLICT;
            case PREASSIGNMENT_CONFLICT -> WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT;
            case GROUP_NAME_REQUIRED, GROUP_NAME_CONFLICT, GROUP_NOT_FOUND, GROUP_NOT_TEMPORARY, GROUP_NOT_DISSOLVABLE ->
                WalkInDirectSeatingError.INVALID_COMMAND;
        };
    }

    private List<DiningTable> loadMemberTables(StoreScope scope, List<TableGroupMember> members) {
        List<DiningTable> tables = new ArrayList<>();
        for (TableGroupMember member : members) {
            DiningTable table = diningTableRepository.findById(scope, member.tableId())
                .orElseThrow(() -> new ApplicationFailure(WalkInDirectSeatingError.INVALID_TABLE_GROUP));
            tables.add(table);
        }
        return tables;
    }

    private void validateTable(DiningTable table, PartySize partySize) {
        require(tableAvailabilityRule.evaluate(table), WalkInDirectSeatingError.TABLE_RESOURCE_UNAVAILABLE);
        require(tableCapacityRule.evaluate(partySize, table.capacity()), WalkInDirectSeatingError.PARTY_SIZE_OUTSIDE_CAPACITY);
    }

    private void validateGroup(TableGroup group, List<TableGroupMember> members, PartySize partySize) {
        require(tableAvailabilityRule.evaluate(group), WalkInDirectSeatingError.INVALID_TABLE_GROUP);
        require(tableGroupValidationRule.evaluate(group, members), WalkInDirectSeatingError.INVALID_TABLE_GROUP);
        require(tableCapacityRule.evaluate(partySize, group.capacity()), WalkInDirectSeatingError.PARTY_SIZE_OUTSIDE_CAPACITY);
    }

    private List<DiningTable> occupyTables(StoreScope scope, ResourceSelection selection) {
        if (selection.table() != null) {
            return List.of(occupyTable(scope, selection.table()));
        }
        List<DiningTable> occupied = new ArrayList<>();
        for (DiningTable memberTable : selection.memberTables()) {
            occupied.add(occupyTable(scope, memberTable));
        }
        return occupied;
    }

    private DiningTable occupyTable(StoreScope scope, DiningTable table) {
        TransitionResult<DiningTableStatus> lockTransition = diningTableStateMachine.validateTransition(table.status(), DiningTableStatus.LOCKED);
        if (!lockTransition.accepted()) {
            throw new ApplicationFailure(WalkInDirectSeatingError.ILLEGAL_STATE_TRANSITION);
        }
        TransitionResult<DiningTableStatus> occupyTransition = diningTableStateMachine.validateTransition(DiningTableStatus.LOCKED, DiningTableStatus.OCCUPIED);
        if (!occupyTransition.accepted()) {
            throw new ApplicationFailure(WalkInDirectSeatingError.ILLEGAL_STATE_TRANSITION);
        }
        return diningTableRepository.save(
            scope,
            new DiningTable(table.id(), table.scope(), table.areaId(), table.tableCode(), table.capacity(), DiningTableStatus.OCCUPIED, table.combinable())
        );
    }

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        SeatWalkInDirectlyCommand command,
        WalkIn walkIn,
        Seating seating,
        TableLock lock,
        ResourceSelection selection
    ) {
        List<BusinessEvent> events = List.of(
            newBusinessEvent("walk_in.created", "walk_in", walkIn.id().value(), command, metadata(selection, null)),
            newBusinessEvent("seating.created", "seating", seating.id().value(), command, metadata(selection, null)),
            newBusinessEvent("table.locked", "table_lock", lock.id(), command, metadata(selection, null)),
            newBusinessEvent("table.occupied", selection.resourceType(), selection.resourceId(), command, metadata(selection, null))
        );
        List<UUID> ids = new ArrayList<>();
        for (BusinessEvent event : events) {
            require(businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()), WalkInDirectSeatingError.BUSINESS_EVENT_WRITE_FAILED);
            try {
                ids.add(businessEventRepository.append(scope, event).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(WalkInDirectSeatingError.BUSINESS_EVENT_WRITE_FAILED);
            }
        }
        return ids;
    }

    private List<UUID> appendTransitionLogs(
        StoreScope scope,
        SeatWalkInDirectlyCommand command,
        WalkIn walkIn,
        Seating seating,
        ResourceSelection selection,
        DiningTable occupiedTable
    ) {
        List<StateTransitionLog> logs = new ArrayList<>();
        logs.add(newTransition("walk_in", walkIn.id().value(), "created", "seated", "walk_in.seat_directly", command, metadata(selection, null)));
        logs.add(newTransition("seating", seating.id().value(), "planned", "occupied", "seating.occupy", command, metadata(selection, null)));
        if (occupiedTable != null) {
            logs.add(newTransition("dining_table", occupiedTable.id().value(), "available", "locked", "dining_table.lock", command, metadata(selection, null)));
            logs.add(newTransition("dining_table", occupiedTable.id().value(), "locked", "occupied", "dining_table.occupy", command, metadata(selection, null)));
        } else {
            logs.add(newTransition("table_group", selection.resourceId(), "active", "occupied", "table_group.occupy", command, metadata(selection, null)));
        }
        List<UUID> ids = new ArrayList<>();
        for (StateTransitionLog log : logs) {
            require(stateTransitionRule.evaluate(log.targetType(), log.targetId(), log.toStatus(), log.transitionCode(), log.actorType()), WalkInDirectSeatingError.STATE_TRANSITION_WRITE_FAILED);
            try {
                ids.add(stateTransitionLogRepository.append(scope, log).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(WalkInDirectSeatingError.STATE_TRANSITION_WRITE_FAILED);
            }
        }
        return ids;
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        SeatWalkInDirectlyCommand command,
        Seating seating,
        Customer customer,
        ResourceSelection selection
    ) {
        String metadata = metadata(selection, """
            ,"customerId":"%s","overrideReasonCode":%s,"overrideNote":%s
            """.formatted(
            customer.id().value(),
            jsonNullable(command.overrideReasonCode()),
            jsonNullable(command.overrideNote())
        ).trim());
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            "walk_in_direct_seating.completed",
            "seating",
            seating.id().value(),
            command.actorType(),
            command.actorType(),
            command.actorId(),
            metadata
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), WalkInDirectSeatingError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInDirectSeatingError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(StoreScope scope, SeatWalkInDirectlyCommand command, WalkInDirectSeatingError error) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    "walk_in_direct_seating.failed",
                    "walk_in_direct_seating",
                    null,
                    command.actorType(),
                    command.actorType(),
                    command.actorId(),
                    "{\"failureReason\":\"" + escape(error.code()) + "\"}"
                )
            );
        } catch (RuntimeException ignored) {
            // The command result already carries the application failure.
        }
    }

    private BusinessEvent newBusinessEvent(String eventType, String targetType, UUID targetId, SeatWalkInDirectlyCommand command, String metadata) {
        return new BusinessEvent(
            UUID.randomUUID(),
            eventType,
            targetType,
            targetId,
            command.actorType(),
            command.actorId(),
            command.actorType(),
            metadata
        );
    }

    private StateTransitionLog newTransition(
        String targetType,
        UUID targetId,
        String fromStatus,
        String toStatus,
        String transitionCode,
        SeatWalkInDirectlyCommand command,
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
            command.actorType(),
            metadata
        );
    }

    private WalkInDirectSeatingResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            WalkInDirectSeatingError error = WalkInDirectSeatingError.fromCode(decision.violationCode());
            if (error == WalkInDirectSeatingError.COMMAND_IN_PROGRESS) {
                return WalkInDirectSeatingResult.retryLater(error);
            }
            return WalkInDirectSeatingResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            return replay(existing.responseSnapshot());
        }
        return WalkInDirectSeatingResult.failure(WalkInDirectSeatingError.IDEMPOTENCY_CONFLICT);
    }

    private WalkInDirectSeatingResult replay(String snapshot) {
        return WalkInDirectSeatingResult.replay(
            UUID.fromString(extract(snapshot, "walkInId")),
            UUID.fromString(extract(snapshot, "seatingId")),
            extract(snapshot, "resourceType"),
            UUID.fromString(extract(snapshot, "resourceId")),
            Integer.parseInt(extract(snapshot, "partySizeSnapshot"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, WalkInDirectSeatingError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static WalkInDirectSeatingError validateCommand(SeatWalkInDirectlyCommand command) {
        if (
            command == null
                || command.tenantId() == null
                || command.storeId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
                || !hasText(command.idempotencyKey())
        ) {
            return WalkInDirectSeatingError.INVALID_COMMAND;
        }
        if (command.partySize() == null || command.partySize() <= 0) {
            return WalkInDirectSeatingError.INVALID_PARTY_SIZE;
        }
        boolean hasTemporaryTables = command.temporaryTableIds() != null && !command.temporaryTableIds().isEmpty();
        int selectedTargets = (command.tableId() == null ? 0 : 1)
            + (command.tableGroupId() == null ? 0 : 1)
            + (hasTemporaryTables ? 1 : 0);
        if (selectedTargets > 1) {
            return WalkInDirectSeatingError.INVALID_RESOURCE_SELECTION;
        }
        if (hasTemporaryTables && command.temporaryTableIds().size() < 2) {
            return WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED;
        }
        if (hasTemporaryTables && command.temporaryTableIds().stream().distinct().count() != command.temporaryTableIds().size()) {
            return WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE;
        }
        return null;
    }

    private static BusinessDate businessDate(Store store) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(store.timezone());
        } catch (RuntimeException exception) {
            zoneId = ZoneId.of("UTC");
        }
        return new BusinessDate(java.time.LocalDate.now(zoneId));
    }

    private static E164Phone parsePhone(String phoneE164) {
        try {
            return hasText(phoneE164) ? new E164Phone(phoneE164.trim()) : E164Phone.empty();
        } catch (IllegalArgumentException exception) {
            throw new ApplicationFailure(WalkInDirectSeatingError.INVALID_CUSTOMER_IDENTITY);
        }
    }

    private static void require(RuleDecision decision, WalkInDirectSeatingError fallback) {
        if (!decision.accepted()) {
            throw new ApplicationFailure(WalkInDirectSeatingError.fromCode(decision.violationCode()) == WalkInDirectSeatingError.INVALID_COMMAND
                ? fallback
                : WalkInDirectSeatingError.fromCode(decision.violationCode()));
        }
    }

    private static String snapshot(UUID walkInId, UUID seatingId, String resourceType, UUID resourceId, int partySizeSnapshot) {
        return """
            {"walkInId":"%s","seatingId":"%s","resourceType":"%s","resourceId":"%s","partySizeSnapshot":%d}
            """.formatted(walkInId, seatingId, resourceType, resourceId, partySizeSnapshot).trim();
    }

    private static String metadata(ResourceSelection selection, String extraJsonWithoutLeadingBrace) {
        String extra = hasText(extraJsonWithoutLeadingBrace) ? extraJsonWithoutLeadingBrace : "";
        return """
            {"resourceType":"%s","resourceId":"%s"%s}
            """.formatted(selection.resourceType(), selection.resourceId(), extra).trim();
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
        Pattern number = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher numberMatcher = number.matcher(json);
        if (numberMatcher.find()) {
            return numberMatcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String values(List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
            .map(UUID::toString)
            .sorted()
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    }

    private record ResourceSelection(
        String resourceType,
        UUID resourceId,
        DiningTable table,
        TableGroup group,
        List<DiningTable> memberTables
    ) {
    }

    private record ExecutionResult(WalkInDirectSeatingResult result) {
    }

    private enum NoopReservationPreassignmentRepository implements ReservationPreassignmentRepositoryPort {
        INSTANCE;

        @Override
        public boolean existsActiveResourceConflict(
            StoreScope scope,
            String resourceType,
            UUID resourceId,
            BusinessDate businessDate,
            com.rpb.reservation.common.time.TimeRange timeRange
        ) {
            return false;
        }

        @Override
        public Set<ReservationResourceAssignment> findActiveResourceAssignmentsForDate(
            StoreScope scope,
            BusinessDate businessDate
        ) {
            return Set.of();
        }

        @Override
        public ReservationPreassignment save(StoreScope scope, ReservationPreassignment preassignment) {
            return preassignment;
        }
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final WalkInDirectSeatingError error;
        private final boolean retryLater;

        private ApplicationFailure(WalkInDirectSeatingError error) {
            this(error, false);
        }

        private ApplicationFailure(WalkInDirectSeatingError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private WalkInDirectSeatingError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
