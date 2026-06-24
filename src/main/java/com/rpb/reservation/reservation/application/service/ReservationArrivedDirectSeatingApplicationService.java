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
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingError;
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingResult;
import com.rpb.reservation.reservation.application.command.SeatArrivedReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.rule.ReservationArrivedSeatingRule;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.state.ReservationStateMachine;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationId;
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
import com.rpb.reservation.table.application.port.out.TableLockRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.domain.TableGroupMember;
import com.rpb.reservation.table.group.rule.DefaultTableGroupValidationRule;
import com.rpb.reservation.table.rule.DefaultTableAvailabilityRule;
import com.rpb.reservation.table.rule.DefaultTableCapacityRule;
import com.rpb.reservation.table.rule.DefaultTableLockRule;
import com.rpb.reservation.table.state.DiningTableStateMachine;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.value.TableGroupId;
import com.rpb.reservation.table.value.TableId;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
public class ReservationArrivedDirectSeatingApplicationService {

    private static final String ACTION = "seat_arrived_reservation";
    private static final String RESERVATION_SOURCE = "reservation";
    private static final String RESOURCE_TABLE = "dining_table";
    private static final String RESOURCE_GROUP = "table_group";
    private static final String EVENT_RESERVATION_SEATED = "reservation.seated";
    private static final String EVENT_SEATING_CREATED = "seating.created";
    private static final String EVENT_TABLE_OCCUPIED = "table.occupied";
    private static final String OPERATION_SEAT = "reservation.seat";
    private static final String OPERATION_SEAT_FAILED = "reservation.seat.failed";

    private final StoreRepositoryPort storeRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final TableLockRepositoryPort tableLockRepository;
    private final SeatingRepositoryPort seatingRepository;
    private final BusinessEventRepositoryPort businessEventRepository;
    private final StateTransitionLogRepositoryPort stateTransitionLogRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final IdempotencyRepositoryPort idempotencyRepository;
    private final Clock clock;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();
    private final DefaultTableAvailabilityRule tableAvailabilityRule = new DefaultTableAvailabilityRule();
    private final DefaultTableCapacityRule tableCapacityRule = new DefaultTableCapacityRule();
    private final DefaultTableLockRule tableLockRule = new DefaultTableLockRule();
    private final DefaultTableGroupValidationRule tableGroupValidationRule = new DefaultTableGroupValidationRule();
    private final DefaultSeatingResourceValidator seatingResourceValidator = new DefaultSeatingResourceValidator();
    private final DefaultAuditRule auditRule = new DefaultAuditRule();
    private final DefaultBusinessEventRule businessEventRule = new DefaultBusinessEventRule();
    private final DefaultStateTransitionRule stateTransitionRule = new DefaultStateTransitionRule();
    private final DefaultIdempotencyRule idempotencyRule = new DefaultIdempotencyRule();
    private final ReservationStateMachine reservationStateMachine = new ReservationStateMachine();
    private final DiningTableStateMachine diningTableStateMachine = new DiningTableStateMachine();
    private final ReservationArrivedSeatingRule reservationArrivedSeatingRule = new ReservationArrivedSeatingRule();

    @Autowired
    public ReservationArrivedDirectSeatingApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        TableLockRepositoryPort tableLockRepository,
        SeatingRepositoryPort seatingRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.reservationRepository = reservationRepository;
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
        this.tableLockRepository = tableLockRepository;
        this.seatingRepository = seatingRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationArrivedDirectSeatingResult seatArrivedReservation(SeatArrivedReservationCommand command) {
        ReservationArrivedDirectSeatingError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return ReservationArrivedDirectSeatingResult.failure(preValidationError);
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
            return ReservationArrivedDirectSeatingResult.failure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? ReservationArrivedDirectSeatingResult.retryLater(failure.error())
                : ReservationArrivedDirectSeatingResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, started.idempotencyKey(), ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED);
            return ReservationArrivedDirectSeatingResult.failure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(SeatArrivedReservationCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.reservationId()),
            value(command.tableId()),
            value(command.tableGroupId()),
            value(command.actorId()),
            normalize(command.actorType()),
            normalize(command.overrideReasonCode()),
            normalize(command.overrideNote()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private ReservationArrivedDirectSeatingResult execute(SeatArrivedReservationCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationArrivedDirectSeatingError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), ReservationArrivedDirectSeatingError.STORE_ACCESS_DENIED);

        Reservation reservation = reservationRepository.findById(scope, new ReservationId(command.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationArrivedDirectSeatingError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.STORE_SCOPE_MISMATCH);
        }
        requireReservationForStoreToday(store, reservation);

        if (reservation.status() == ReservationStatus.SEATED) {
            return alreadySeated(scope, command, started, reservation);
        }

        ReservationArrivedDirectSeatingError statusError = reservationArrivedSeatingRule.validateFreshSeating(reservation.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }
        if (!reservationStateMachine.canTransition(ReservationStatus.ARRIVED, ReservationStatus.SEATED)) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.ILLEGAL_STATE_TRANSITION);
        }

        ResourceSelection selection = resolveResource(command, scope, reservation.partySize());
        require(reservationArrivedSeatingRule.validateReservationSource(reservation.id().value()), ReservationArrivedDirectSeatingError.INVALID_SEATING_SOURCE);
        require(seatingResourceValidator.validate(selection.resourceType(), selection.resourceId()), ReservationArrivedDirectSeatingError.INVALID_SEATING_RESOURCE);

        Seating seating = createSeating(scope, command, reservation);
        SeatingResource seatingResource = createSeatingResource(scope, seating, selection);
        List<DiningTable> occupiedTables = occupyTables(scope, selection);
        Reservation savedReservation = saveSeated(scope, reservation);
        List<String> groupMemberStatuses = occupiedTables.stream().map(table -> table.status().code()).toList();
        String tableStatus = selection.isTable() ? occupiedTables.getFirst().status().code() : null;
        List<UUID> occupiedTableIds = occupiedTables.stream().map(table -> table.id().value()).toList();

        List<UUID> eventIds = appendBusinessEvents(scope, command, savedReservation, seating, selection, started.idempotencyKey());
        List<UUID> transitionIds = appendTransitionLogs(scope, command, savedReservation, seating, occupiedTables, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, savedReservation, seating, selection, occupiedTableIds, started.idempotencyKey());

        IdempotencyRecord completed = completeIdempotency(
            scope,
            started,
            savedReservation,
            seating,
            seatingResource,
            tableStatus,
            selection.isTable() ? List.of() : groupMemberStatuses,
            false
        );

        return ReservationArrivedDirectSeatingResult.success(
            savedReservation.id().value(),
            savedReservation.reservationCode().value(),
            seating.id().value(),
            seatingResource.resourceType(),
            seatingResource.resourceId(),
            seating.partySizeSnapshot().value(),
            tableStatus,
            selection.isTable() ? List.of() : groupMemberStatuses,
            occupiedTableIds,
            completed.status().code(),
            List.of(EVENT_RESERVATION_SEATED, EVENT_SEATING_CREATED, EVENT_TABLE_OCCUPIED),
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private void requireReservationForStoreToday(Store store, Reservation reservation) {
        LocalDate storeToday = LocalDate.now(clock.withZone(ZoneId.of(store.timezone())));
        if (!reservation.businessDate().value().equals(storeToday)) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.RESERVATION_NOT_TODAY);
        }
    }

    private ReservationArrivedDirectSeatingResult alreadySeated(
        StoreScope scope,
        SeatArrivedReservationCommand command,
        IdempotencyRecord started,
        Reservation reservation
    ) {
        Seating seating = seatingRepository.findActiveBySource(scope, RESERVATION_SOURCE, reservation.id().value())
            .orElseThrow(() -> new ApplicationFailure(ReservationArrivedDirectSeatingError.RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING));
        SeatingResource seatingResource = seatingRepository.findActiveResourceBySeating(scope, seating.id())
            .filter(resource -> scope.equals(resource.scope()))
            .orElseThrow(() -> new ApplicationFailure(ReservationArrivedDirectSeatingError.RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING));

        String tableStatus = null;
        List<String> groupMemberStatuses = List.of();
        List<UUID> occupiedTableIds = List.of();
        if (RESOURCE_TABLE.equals(seatingResource.resourceType())) {
            tableStatus = diningTableRepository.findById(scope, new TableId(seatingResource.resourceId()))
                .map(table -> table.status().code())
                .orElse(null);
            occupiedTableIds = List.of(seatingResource.resourceId());
        } else if (RESOURCE_GROUP.equals(seatingResource.resourceType())) {
            List<DiningTable> memberTables = tableGroupRepository.findActiveMembers(scope, new TableGroupId(seatingResource.resourceId())).stream()
                .map(member -> diningTableRepository.findById(scope, member.tableId()))
                .flatMap(Optional::stream)
                .toList();
            groupMemberStatuses = memberTables.stream().map(table -> table.status().code()).toList();
            occupiedTableIds = memberTables.stream().map(table -> table.id().value()).toList();
        }

        IdempotencyRecord completed = completeIdempotency(
            scope,
            started,
            reservation,
            seating,
            seatingResource,
            tableStatus,
            groupMemberStatuses,
            true
        );

        return ReservationArrivedDirectSeatingResult.alreadySeated(
            reservation.id().value(),
            reservation.reservationCode().value(),
            seating.id().value(),
            seatingResource.resourceType(),
            seatingResource.resourceId(),
            seating.partySizeSnapshot().value(),
            seating.status().code(),
            seatingResource.status(),
            tableStatus,
            groupMemberStatuses,
            occupiedTableIds,
            completed.status().code()
        );
    }

    private ResourceSelection resolveResource(SeatArrivedReservationCommand command, StoreScope scope, PartySize partySize) {
        if (command.tableId() != null) {
            DiningTable table = diningTableRepository.findById(scope, new TableId(command.tableId()))
                .orElseThrow(() -> new ApplicationFailure(ReservationArrivedDirectSeatingError.TABLE_NOT_FOUND));
            validateTable(scope, table, partySize, ReservationArrivedDirectSeatingError.TABLE_NOT_AVAILABLE);
            return new ResourceSelection(RESOURCE_TABLE, table.id().value(), table, null, List.of());
        }

        TableGroup group = tableGroupRepository.findById(scope, new TableGroupId(command.tableGroupId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationArrivedDirectSeatingError.TABLE_GROUP_NOT_FOUND));
        require(tableAvailabilityRule.evaluate(group), ReservationArrivedDirectSeatingError.TABLE_GROUP_INVALID);
        List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, group.id());
        require(tableGroupValidationRule.evaluate(group, members), ReservationArrivedDirectSeatingError.TABLE_GROUP_INVALID);
        require(tableCapacityRule.evaluate(partySize, group.capacity()), ReservationArrivedDirectSeatingError.TABLE_GROUP_CAPACITY_INSUFFICIENT);
        requireNoLock(scope, RESOURCE_GROUP, group.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_GROUP, group.id().value())) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.TABLE_GROUP_INVALID);
        }

        List<DiningTable> memberTables = new ArrayList<>();
        for (TableGroupMember member : members) {
            DiningTable table = diningTableRepository.findById(scope, member.tableId())
                .orElseThrow(() -> new ApplicationFailure(ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE));
            validateGroupMemberTable(scope, table);
            memberTables.add(table);
        }
        return new ResourceSelection(RESOURCE_GROUP, group.id().value(), null, group, memberTables);
    }

    private void validateTable(
        StoreScope scope,
        DiningTable table,
        PartySize partySize,
        ReservationArrivedDirectSeatingError unavailableError
    ) {
        require(tableAvailabilityRule.evaluate(table), unavailableError);
        ReservationArrivedDirectSeatingError capacityError = unavailableError == ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE
            ? ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE
            : ReservationArrivedDirectSeatingError.TABLE_CAPACITY_INSUFFICIENT;
        require(tableCapacityRule.evaluate(partySize, table.capacity()), capacityError);
        requireNoLock(scope, RESOURCE_TABLE, table.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_TABLE, table.id().value())) {
            throw new ApplicationFailure(unavailableError);
        }
    }

    private void validateGroupMemberTable(StoreScope scope, DiningTable table) {
        require(tableAvailabilityRule.evaluate(table), ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE);
        requireNoLock(scope, RESOURCE_TABLE, table.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_TABLE, table.id().value())) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE);
        }
    }

    private void requireNoLock(StoreScope scope, String resourceType, UUID resourceId) {
        require(
            tableLockRule.evaluate(tableLockRepository.existsActiveConflict(scope, resourceType, resourceId, OffsetDateTime.now(clock))),
            ReservationArrivedDirectSeatingError.TABLE_LOCK_CONFLICT
        );
    }

    private Seating createSeating(StoreScope scope, SeatArrivedReservationCommand command, Reservation reservation) {
        UUID seatingUuid = UUID.randomUUID();
        try {
            return seatingRepository.save(
                scope,
                new Seating(
                    new SeatingId(seatingUuid),
                    scope,
                    RESERVATION_SOURCE,
                    reservation.id().value(),
                    "S-" + seatingUuid.toString().substring(0, 8),
                    blankToNull(command.overrideReasonCode()),
                    seatingNote(command),
                    reservation.partySize(),
                    SeatingStatus.OCCUPIED
                )
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED, exception);
        }
    }

    private SeatingResource createSeatingResource(StoreScope scope, Seating seating, ResourceSelection selection) {
        try {
            return seatingRepository.saveResource(
                scope,
                new SeatingResource(UUID.randomUUID(), scope, seating.id(), selection.resourceType(), selection.resourceId(), "active")
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED, exception);
        }
    }

    private List<DiningTable> occupyTables(StoreScope scope, ResourceSelection selection) {
        if (selection.isTable()) {
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
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.ILLEGAL_STATE_TRANSITION);
        }
        TransitionResult<DiningTableStatus> occupyTransition = diningTableStateMachine.validateTransition(DiningTableStatus.LOCKED, DiningTableStatus.OCCUPIED);
        if (!occupyTransition.accepted()) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.ILLEGAL_STATE_TRANSITION);
        }
        try {
            return diningTableRepository.save(
                scope,
                new DiningTable(table.id(), table.scope(), table.areaId(), table.tableCode(), table.capacity(), DiningTableStatus.OCCUPIED, table.combinable())
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED, exception);
        }
    }

    private Reservation saveSeated(StoreScope scope, Reservation reservation) {
        try {
            return reservationRepository.save(
                scope,
                new Reservation(
                    reservation.id(),
                    reservation.scope(),
                    reservation.customerId(),
                    reservation.reservationCode(),
                    reservation.partySize(),
                    reservation.businessDate(),
                    reservation.reservedStartAt(),
                    reservation.reservedEndAt(),
                    reservation.holdUntilAt(),
                    ReservationStatus.SEATED,
                    reservation.sourceChannel(),
                    reservation.cancellationReasonCode(),
                    reservation.noShowReasonCode(),
                    reservation.note(),
                    reservation.createdAt(),
                    Instant.now(clock),
                    reservation.deletedAt()
                )
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED, exception);
        }
    }

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        SeatArrivedReservationCommand command,
        Reservation reservation,
        Seating seating,
        ResourceSelection selection,
        IdempotencyKey idempotencyKey
    ) {
        List<BusinessEvent> events = List.of(
            newBusinessEvent(EVENT_RESERVATION_SEATED, RESERVATION_SOURCE, reservation.id().value(), command, metadata(reservation, seating, selection, idempotencyKey, null)),
            newBusinessEvent(EVENT_SEATING_CREATED, "seating", seating.id().value(), command, metadata(reservation, seating, selection, idempotencyKey, null)),
            newBusinessEvent(EVENT_TABLE_OCCUPIED, selection.resourceType(), selection.resourceId(), command, metadata(reservation, seating, selection, idempotencyKey, null))
        );
        List<UUID> ids = new ArrayList<>();
        for (BusinessEvent event : events) {
            require(
                businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
                ReservationArrivedDirectSeatingError.BUSINESS_EVENT_WRITE_FAILED
            );
            try {
                ids.add(businessEventRepository.append(scope, event).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(ReservationArrivedDirectSeatingError.BUSINESS_EVENT_WRITE_FAILED, exception);
            }
        }
        return ids;
    }

    private List<UUID> appendTransitionLogs(
        StoreScope scope,
        SeatArrivedReservationCommand command,
        Reservation reservation,
        Seating seating,
        List<DiningTable> occupiedTables,
        IdempotencyKey idempotencyKey
    ) {
        List<StateTransitionLog> logs = new ArrayList<>();
        String metadata = metadata(reservation, seating, null, idempotencyKey, null);
        logs.add(newTransition(RESERVATION_SOURCE, reservation.id().value(), "arrived", "seated", OPERATION_SEAT, command, metadata));
        logs.add(newTransition("seating", seating.id().value(), "planned", "occupied", "seating.occupy", command, metadata));
        for (DiningTable table : occupiedTables) {
            logs.add(newTransition("dining_table", table.id().value(), "available", "occupied", "dining_table.occupy", command, metadata));
        }
        List<UUID> ids = new ArrayList<>();
        for (StateTransitionLog log : logs) {
            require(
                stateTransitionRule.evaluate(log.targetType(), log.targetId(), log.toStatus(), log.transitionCode(), log.actorType()),
                ReservationArrivedDirectSeatingError.STATE_TRANSITION_WRITE_FAILED
            );
            try {
                ids.add(stateTransitionLogRepository.append(scope, log).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(ReservationArrivedDirectSeatingError.STATE_TRANSITION_WRITE_FAILED, exception);
            }
        }
        return ids;
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        SeatArrivedReservationCommand command,
        Reservation reservation,
        Seating seating,
        ResourceSelection selection,
        List<UUID> occupiedTableIds,
        IdempotencyKey idempotencyKey
    ) {
        String extra = """
            ,"occupiedTableIds":%s,"overrideReasonCode":%s,"overrideNote":%s,"note":%s
            """.formatted(
            jsonArray(occupiedTableIds),
            jsonNullable(command.overrideReasonCode()),
            jsonNullable(command.overrideNote()),
            jsonNullable(command.note())
        ).trim();
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_SEAT,
            RESERVATION_SOURCE,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, seating, selection, idempotencyKey, extra)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), ReservationArrivedDirectSeatingError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        SeatArrivedReservationCommand command,
        IdempotencyKey idempotencyKey,
        ReservationArrivedDirectSeatingError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_SEAT_FAILED,
                    RESERVATION_SOURCE,
                    command.reservationId(),
                    source(command),
                    command.actorType(),
                    command.actorId(),
                    """
                        {"failureReason":"%s","reservationId":"%s","tableId":%s,"tableGroupId":%s,"idempotencyKey":"%s"}
                        """.formatted(
                        error.code(),
                        command.reservationId(),
                        jsonNullable(command.tableId()),
                        jsonNullable(command.tableGroupId()),
                        escape(idempotencyKey.value())
                    ).trim()
                )
            );
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private BusinessEvent newBusinessEvent(
        String eventType,
        String targetType,
        UUID targetId,
        SeatArrivedReservationCommand command,
        String metadata
    ) {
        return new BusinessEvent(
            UUID.randomUUID(),
            eventType,
            targetType,
            targetId,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata
        );
    }

    private StateTransitionLog newTransition(
        String targetType,
        UUID targetId,
        String fromStatus,
        String toStatus,
        String transitionCode,
        SeatArrivedReservationCommand command,
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
            source(command),
            metadata
        );
    }

    private IdempotencyRecord completeIdempotency(
        StoreScope scope,
        IdempotencyRecord started,
        Reservation reservation,
        Seating seating,
        SeatingResource seatingResource,
        String tableStatus,
        List<String> groupMemberStatuses,
        boolean alreadySeated
    ) {
        String snapshot = snapshot(reservation, seating, seatingResource, tableStatus, groupMemberStatuses, alreadySeated);
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
            throw new ApplicationFailure(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED, exception);
        }
    }

    private ReservationArrivedDirectSeatingResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationArrivedDirectSeatingError error = ReservationArrivedDirectSeatingError.fromCode(decision.violationCode());
            if (error == ReservationArrivedDirectSeatingError.COMMAND_IN_PROGRESS) {
                return ReservationArrivedDirectSeatingResult.retryLater(error);
            }
            return ReservationArrivedDirectSeatingResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return ReservationArrivedDirectSeatingResult.failure(ReservationArrivedDirectSeatingError.IDEMPOTENCY_CONFLICT);
            }
        }
        return ReservationArrivedDirectSeatingResult.failure(ReservationArrivedDirectSeatingError.IDEMPOTENCY_CONFLICT);
    }

    private ReservationArrivedDirectSeatingResult replay(String snapshot) {
        return ReservationArrivedDirectSeatingResult.replay(
            UUID.fromString(extract(snapshot, "reservationId")),
            extract(snapshot, "reservationCode"),
            UUID.fromString(extract(snapshot, "seatingId")),
            extract(snapshot, "resourceType"),
            UUID.fromString(extract(snapshot, "resourceId")),
            Integer.parseInt(extractNumber(snapshot, "partySizeSnapshot")),
            extract(snapshot, "reservationStatus"),
            extract(snapshot, "seatingStatus"),
            extract(snapshot, "seatingResourceStatus"),
            extractNullableString(snapshot, "tableStatus"),
            extractStringArray(snapshot, "groupMemberStatuses"),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadySeated"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationArrivedDirectSeatingError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static ReservationArrivedDirectSeatingError validateCommand(SeatArrivedReservationCommand command) {
        if (command == null) {
            return ReservationArrivedDirectSeatingError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationArrivedDirectSeatingError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationArrivedDirectSeatingError.INVALID_COMMAND;
        }
        if (command.tableId() != null && command.tableGroupId() != null) {
            return ReservationArrivedDirectSeatingError.RESOURCE_SELECTION_CONFLICT;
        }
        if (command.tableId() == null && command.tableGroupId() == null) {
            return ReservationArrivedDirectSeatingError.RESOURCE_SELECTION_REQUIRED;
        }
        return null;
    }

    private static ReservationArrivedDirectSeatingError fromRuleCode(String code, ReservationArrivedDirectSeatingError fallback) {
        if (
            fallback == ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE
                && ("table_resource_unavailable".equals(code) || "party_size_outside_capacity".equals(code))
        ) {
            return fallback;
        }
        if (
            fallback == ReservationArrivedDirectSeatingError.TABLE_GROUP_CAPACITY_INSUFFICIENT
                && "party_size_outside_capacity".equals(code)
        ) {
            return fallback;
        }
        ReservationArrivedDirectSeatingError error = ReservationArrivedDirectSeatingError.fromCode(code);
        return error == ReservationArrivedDirectSeatingError.INVALID_COMMAND ? fallback : error;
    }

    private static void require(RuleDecision decision, ReservationArrivedDirectSeatingError fallback) {
        if (!decision.accepted()) {
            throw new ApplicationFailure(fromRuleCode(decision.violationCode(), fallback));
        }
    }

    private static String snapshot(
        Reservation reservation,
        Seating seating,
        SeatingResource seatingResource,
        String tableStatus,
        List<String> groupMemberStatuses,
        boolean alreadySeated
    ) {
        return """
            {"reservationId":"%s","reservationCode":"%s","reservationStatus":"seated","seatingId":"%s","resourceType":"%s","resourceId":"%s","partySizeSnapshot":%d,"seatingStatus":"%s","seatingResourceStatus":"%s","tableStatus":%s,"groupMemberStatuses":%s,"alreadySeated":%s}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            seating.id().value(),
            seatingResource.resourceType(),
            seatingResource.resourceId(),
            seating.partySizeSnapshot().value(),
            seating.status().code(),
            seatingResource.status(),
            jsonNullable(tableStatus),
            jsonArrayStrings(groupMemberStatuses),
            alreadySeated
        ).trim();
    }

    private static String metadata(
        Reservation reservation,
        Seating seating,
        ResourceSelection selection,
        IdempotencyKey idempotencyKey,
        String extraJsonWithoutLeadingBrace
    ) {
        String resourceType = selection == null ? null : selection.resourceType();
        UUID resourceId = selection == null ? null : selection.resourceId();
        String extra = hasText(extraJsonWithoutLeadingBrace) ? extraJsonWithoutLeadingBrace : "";
        return """
            {"reservationId":"%s","reservationCode":"%s","seatingId":"%s","resourceType":%s,"resourceId":%s,"beforeReservationStatus":"arrived","afterReservationStatus":"seated","idempotencyKey":"%s"%s}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            seating.id().value(),
            jsonNullable(resourceType),
            jsonNullable(resourceId),
            escape(idempotencyKey.value()),
            extra
        ).trim();
    }

    private static String seatingNote(SeatArrivedReservationCommand command) {
        if (hasText(command.note())) {
            return command.note().trim();
        }
        return blankToNull(command.overrideNote());
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

    private static List<String> extractStringArray(String json, String key) {
        Pattern array = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)]");
        Matcher matcher = array.matcher(json);
        if (!matcher.find()) {
            return List.of();
        }
        String content = matcher.group(1);
        if (!hasText(content)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher valueMatcher = Pattern.compile("\"([^\"]*)\"").matcher(content);
        while (valueMatcher.find()) {
            values.add(valueMatcher.group(1));
        }
        return values;
    }

    private static String jsonArray(List<UUID> values) {
        return values.stream().map(UUID::toString).map(value -> "\"" + escape(value) + "\"").toList().toString();
    }

    private static String jsonArrayStrings(List<String> values) {
        return values.stream().map(value -> "\"" + escape(value) + "\"").toList().toString();
    }

    private static String jsonNullable(String value) {
        return hasText(value) ? "\"" + escape(value.trim()) + "\"" : "null";
    }

    private static String jsonNullable(UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String source(SeatArrivedReservationCommand command) {
        return hasText(command.actorType()) ? command.actorType().trim() : "staff";
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

    private record ResourceSelection(
        String resourceType,
        UUID resourceId,
        DiningTable table,
        TableGroup group,
        List<DiningTable> memberTables
    ) {
        private boolean isTable() {
            return RESOURCE_TABLE.equals(resourceType);
        }
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final ReservationArrivedDirectSeatingError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationArrivedDirectSeatingError error) {
            this(error, false);
        }

        private ApplicationFailure(ReservationArrivedDirectSeatingError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(ReservationArrivedDirectSeatingError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(ReservationArrivedDirectSeatingError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationArrivedDirectSeatingError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
