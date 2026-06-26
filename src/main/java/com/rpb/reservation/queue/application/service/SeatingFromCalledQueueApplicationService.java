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
import com.rpb.reservation.common.state.TransitionResult;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.queue.application.SeatingFromCalledQueueError;
import com.rpb.reservation.queue.application.SeatingFromCalledQueueResult;
import com.rpb.reservation.queue.application.command.SeatCalledQueueTicketCommand;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.rule.QueueTicketSeatRule;
import com.rpb.reservation.queue.application.rule.SeatingFromCalledQueueRule;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.state.QueueTicketStateMachine;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
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
import com.rpb.reservation.table.application.TemporaryTableGroupError;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import com.rpb.reservation.table.application.service.TemporaryTableGroupApplicationService;
import com.rpb.reservation.table.application.service.TemporaryTableGroupCommand;
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
import com.rpb.reservation.walkin.application.port.out.WalkInRepositoryPort;
import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.value.WalkInId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
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
public class SeatingFromCalledQueueApplicationService {

    private static final String ACTION = "seat_called_queue_ticket";
    private static final String QUEUE_TICKET_SOURCE = "queue_ticket";
    private static final String TARGET_RESERVATION = "reservation";
    private static final String TARGET_WALK_IN = "walk_in";
    private static final String TARGET_SEATING = "seating";
    private static final String TARGET_DINING_TABLE = "dining_table";
    private static final String RESOURCE_TABLE = "dining_table";
    private static final String RESOURCE_GROUP = "table_group";
    private static final String EVENT_QUEUE_TICKET_SEATED = "queue_ticket.seated";
    private static final String EVENT_RESERVATION_SEATED = "reservation.seated";
    private static final String EVENT_WALK_IN_SEATED = "walk_in.seated";
    private static final String EVENT_SEATING_CREATED = "seating.created";
    private static final String EVENT_TABLE_OCCUPIED = "table.occupied";
    private static final String OPERATION_SEAT = "queue.seat";
    private static final String OPERATION_SEAT_FAILED = "queue.seat.failed";
    private static final String TRANSITION_QUEUE_TICKET_SEAT = "queue_ticket.seat";
    private static final String TRANSITION_RESERVATION_SEAT = "reservation.seat";
    private static final String TRANSITION_WALK_IN_SEAT_FROM_QUEUE = "walk_in.seat_from_queue";
    private static final String TRANSITION_SEATING_OCCUPY = "seating.occupy";
    private static final String TRANSITION_TABLE_OCCUPY = "dining_table.occupy";

    private final StoreRepositoryPort storeRepository;
    private final QueueTicketRepositoryPort queueTicketRepository;
    private final ReservationRepositoryPort reservationRepository;
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
    private final Clock clock;
    private final TemporaryTableGroupApplicationService temporaryTableGroupService;
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
    private final QueueTicketStateMachine queueTicketStateMachine = new QueueTicketStateMachine();
    private final ReservationStateMachine reservationStateMachine = new ReservationStateMachine();
    private final DiningTableStateMachine diningTableStateMachine = new DiningTableStateMachine();
    private final QueueTicketSeatRule queueTicketSeatRule = new QueueTicketSeatRule();
    private final SeatingFromCalledQueueRule seatingFromCalledQueueRule = new SeatingFromCalledQueueRule();

    public SeatingFromCalledQueueApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
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
        this(
            storeRepository,
            queueTicketRepository,
            reservationRepository,
            diningTableRepository,
            tableGroupRepository,
            tableLockRepository,
            NoopReservationPreassignmentRepository.INSTANCE,
            NoopWalkInRepository.INSTANCE,
            seatingRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            clock
        );
    }

    public SeatingFromCalledQueueApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        ReservationRepositoryPort reservationRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        TableLockRepositoryPort tableLockRepository,
        WalkInRepositoryPort walkInRepository,
        SeatingRepositoryPort seatingRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this(
            storeRepository,
            queueTicketRepository,
            reservationRepository,
            diningTableRepository,
            tableGroupRepository,
            tableLockRepository,
            NoopReservationPreassignmentRepository.INSTANCE,
            walkInRepository,
            seatingRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            clock
        );
    }

    @Autowired
    public SeatingFromCalledQueueApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        ReservationRepositoryPort reservationRepository,
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
        this(
            storeRepository,
            queueTicketRepository,
            reservationRepository,
            diningTableRepository,
            tableGroupRepository,
            tableLockRepository,
            preassignmentRepository,
            walkInRepository,
            seatingRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            Clock.systemUTC()
        );
    }

    public SeatingFromCalledQueueApplicationService(
        StoreRepositoryPort storeRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        ReservationRepositoryPort reservationRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        TableLockRepositoryPort tableLockRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        WalkInRepositoryPort walkInRepository,
        SeatingRepositoryPort seatingRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.reservationRepository = reservationRepository;
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
        this.clock = clock;
        this.temporaryTableGroupService = new TemporaryTableGroupApplicationService(
            diningTableRepository,
            tableGroupRepository,
            tableLockRepository,
            preassignmentRepository,
            seatingRepository,
            clock
        );
    }

    @Transactional
    public SeatingFromCalledQueueResult seatCalledQueueTicket(SeatCalledQueueTicketCommand command) {
        SeatingFromCalledQueueError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return SeatingFromCalledQueueResult.failure(preValidationError);
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
            return SeatingFromCalledQueueResult.failure(SeatingFromCalledQueueError.PERSISTENCE_ERROR);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? SeatingFromCalledQueueResult.retryLater(failure.error())
                : SeatingFromCalledQueueResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, SeatingFromCalledQueueError.PERSISTENCE_ERROR);
            appendFailureAudit(scope, command, started.idempotencyKey(), SeatingFromCalledQueueError.PERSISTENCE_ERROR);
            return SeatingFromCalledQueueResult.failure(SeatingFromCalledQueueError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(SeatCalledQueueTicketCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.queueTicketId()),
            value(command.tableId()),
            value(command.tableGroupId()),
            values(command.temporaryTableIds()),
            value(command.actorId()),
            normalize(command.actorType()),
            normalize(command.overrideReasonCode()),
            normalize(command.overrideNote()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private SeatingFromCalledQueueResult execute(SeatCalledQueueTicketCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), SeatingFromCalledQueueError.STORE_ACCESS_DENIED);

        QueueTicket queueTicket = queueTicketRepository.findById(scope, new QueueTicketId(command.queueTicketId()))
            .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.QUEUE_TICKET_NOT_FOUND));
        if (!scope.equals(queueTicket.scope())) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.STORE_SCOPE_MISMATCH);
        }

        if (queueTicket.status() == QueueTicketStatus.SEATED) {
            return alreadySeated(scope, command, started, queueTicket);
        }

        SeatingFromCalledQueueError statusError = queueTicketSeatRule.validateFreshSeating(queueTicket);
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }

        Optional<Reservation> reservation = loadReservationIfPresent(scope, queueTicket);
        Optional<WalkIn> walkIn = loadWalkInIfPresent(scope, queueTicket);
        validateQueueTicketSource(reservation, walkIn);
        if (reservation.isPresent() && reservation.get().status() == ReservationStatus.SEATED) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED);
        }
        if (reservation.isPresent() && reservation.get().status() != ReservationStatus.ARRIVED) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.RESERVATION_STATUS_NOT_ARRIVED);
        }
        if (walkIn.isPresent() && !"queued".equals(walkIn.get().status())) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.ILLEGAL_STATE_TRANSITION);
        }
        if (!queueTicketStateMachine.canTransition(QueueTicketStatus.CALLED, QueueTicketStatus.SEATED)
            || (reservation.isPresent() && !reservationStateMachine.canTransition(ReservationStatus.ARRIVED, ReservationStatus.SEATED))) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.ILLEGAL_STATE_TRANSITION);
        }

        ResourceSelection selection = resolveResource(command, scope, queueTicket, reservation);
        validatePreassignment(scope, reservation, selection);
        require(seatingFromCalledQueueRule.validateQueueTicketSource(queueTicket.id().value()), SeatingFromCalledQueueError.INVALID_SEATING_SOURCE);
        require(seatingResourceValidator.validate(selection.resourceType(), selection.resourceId()), SeatingFromCalledQueueError.INVALID_SEATING_RESOURCE);

        Seating seating = createSeating(scope, command, queueTicket);
        SeatingResource seatingResource = createSeatingResource(scope, seating, selection);
        List<DiningTable> occupiedTables = occupyTables(scope, selection);
        Optional<Reservation> savedReservation = reservation.map(value -> saveSeated(scope, value));
        Optional<WalkIn> savedWalkIn = walkIn.map(value -> saveSeated(scope, value));
        QueueTicket savedQueueTicket = saveSeated(scope, queueTicket);
        List<String> groupMemberStatuses = occupiedTables.stream().map(table -> table.status().code()).toList();
        String tableStatus = selection.isTable() ? occupiedTables.getFirst().status().code() : null;
        List<UUID> occupiedTableIds = occupiedTables.stream().map(table -> table.id().value()).toList();

        List<UUID> eventIds = appendBusinessEvents(scope, command, savedQueueTicket, savedReservation, savedWalkIn, seating, selection, started.idempotencyKey());
        List<UUID> transitionIds = appendTransitionLogs(scope, command, savedQueueTicket, savedReservation, savedWalkIn, seating, occupiedTables, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, savedQueueTicket, savedReservation, savedWalkIn, seating, selection, occupiedTableIds, started.idempotencyKey());
        List<String> eventNames = new ArrayList<>();
        eventNames.add(EVENT_QUEUE_TICKET_SEATED);
        savedReservation.ifPresent(value -> eventNames.add(EVENT_RESERVATION_SEATED));
        savedWalkIn.ifPresent(value -> eventNames.add(EVENT_WALK_IN_SEATED));
        eventNames.add(EVENT_SEATING_CREATED);
        eventNames.add(EVENT_TABLE_OCCUPIED);

        IdempotencyRecord completed = completeIdempotency(
            scope,
            started,
            savedQueueTicket,
            savedReservation,
            seating,
            seatingResource,
            tableStatus,
            selection.isTable() ? List.of() : groupMemberStatuses,
            false
        );

        return SeatingFromCalledQueueResult.success(
            savedQueueTicket.id().value(),
            savedQueueTicket.ticketNumber().value(),
            savedReservation.map(value -> value.id().value()).orElse(null),
            savedReservation.map(value -> value.reservationCode().value()).orElse(null),
            seating.id().value(),
            seatingResource.resourceType(),
            seatingResource.resourceId(),
            seating.partySizeSnapshot().value(),
            tableStatus,
            selection.isTable() ? List.of() : groupMemberStatuses,
            occupiedTableIds,
            completed.status().code(),
            eventNames,
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private SeatingFromCalledQueueResult alreadySeated(
        StoreScope scope,
        SeatCalledQueueTicketCommand command,
        IdempotencyRecord started,
        QueueTicket queueTicket
    ) {
        Optional<Reservation> reservation = loadReservationIfPresent(scope, queueTicket);
        if (reservation.isPresent() && reservation.get().status() != ReservationStatus.SEATED) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED);
        }
        Seating seating = seatingRepository.findActiveBySource(scope, QUEUE_TICKET_SOURCE, queueTicket.id().value())
            .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING));
        SeatingResource seatingResource = seatingRepository.findActiveResourceBySeating(scope, seating.id())
            .filter(resource -> scope.equals(resource.scope()))
            .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE));

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
            queueTicket,
            reservation,
            seating,
            seatingResource,
            tableStatus,
            groupMemberStatuses,
            true
        );

        return SeatingFromCalledQueueResult.alreadySeated(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            reservation.map(value -> value.id().value()).orElse(null),
            reservation.map(value -> value.reservationCode().value()).orElse(null),
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

    private Optional<Reservation> loadReservationIfPresent(StoreScope scope, QueueTicket queueTicket) {
        if (queueTicket.reservationId() == null) {
            return Optional.empty();
        }
        Reservation reservation = reservationRepository.findById(scope, new ReservationId(queueTicket.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.STORE_SCOPE_MISMATCH);
        }
        return Optional.of(reservation);
    }

    private Optional<WalkIn> loadWalkInIfPresent(StoreScope scope, QueueTicket queueTicket) {
        if (queueTicket.walkInId() == null) {
            return Optional.empty();
        }
        WalkIn walkIn = walkInRepository.findById(scope, new WalkInId(queueTicket.walkInId()))
            .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.QUEUE_TICKET_SOURCE_NOT_RESERVATION));
        if (!scope.equals(walkIn.scope())) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.STORE_SCOPE_MISMATCH);
        }
        return Optional.of(walkIn);
    }

    private static void validateQueueTicketSource(Optional<Reservation> reservation, Optional<WalkIn> walkIn) {
        if (reservation.isPresent() == walkIn.isPresent()) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.QUEUE_TICKET_SOURCE_NOT_RESERVATION);
        }
    }

    private ResourceSelection resolveResource(
        SeatCalledQueueTicketCommand command,
        StoreScope scope,
        QueueTicket queueTicket,
        Optional<Reservation> reservation
    ) {
        PartySize partySize = queueTicket.partySize();
        if (!command.temporaryTableIds().isEmpty()) {
            TemporaryTableGroupResult result = temporaryTableGroupService.createForSeating(new TemporaryTableGroupCommand(
                scope,
                command.temporaryTableIds(),
                partySize,
                reservation.map(Reservation::businessDate).orElse(queueTicket.businessDate()),
                reservation.map(value -> value.id().value()).orElse(queueTicket.id().value())
            ));
            if (!result.success()) {
                throw new ApplicationFailure(temporaryTableGroupError(result.error()));
            }
            return new ResourceSelection(RESOURCE_GROUP, result.group().id().value(), null, result.group(), result.memberTables(), true);
        }
        if (command.tableId() != null) {
            DiningTable table = diningTableRepository.findById(scope, new TableId(command.tableId()))
                .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.TABLE_NOT_FOUND));
            validateTable(scope, table, partySize, SeatingFromCalledQueueError.TABLE_NOT_AVAILABLE);
            return new ResourceSelection(RESOURCE_TABLE, table.id().value(), table, null, List.of(), false);
        }

        TableGroup group = tableGroupRepository.findById(scope, new TableGroupId(command.tableGroupId()))
            .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.TABLE_GROUP_NOT_FOUND));
        require(tableAvailabilityRule.evaluate(group), SeatingFromCalledQueueError.TABLE_GROUP_INVALID);
        List<TableGroupMember> members = tableGroupRepository.findActiveMembers(scope, group.id());
        require(tableGroupValidationRule.evaluate(group, members), SeatingFromCalledQueueError.TABLE_GROUP_INVALID);
        require(tableCapacityRule.evaluate(partySize, group.capacity()), SeatingFromCalledQueueError.TABLE_GROUP_CAPACITY_INSUFFICIENT);
        requireNoLock(scope, RESOURCE_GROUP, group.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_GROUP, group.id().value())) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.TABLE_GROUP_INVALID);
        }

        List<DiningTable> memberTables = new ArrayList<>();
        for (TableGroupMember member : members) {
            DiningTable table = diningTableRepository.findById(scope, member.tableId())
                .orElseThrow(() -> new ApplicationFailure(SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE));
            validateGroupMemberTable(scope, table);
            memberTables.add(table);
        }
        return new ResourceSelection(RESOURCE_GROUP, group.id().value(), null, group, memberTables, false);
    }

    private static SeatingFromCalledQueueError temporaryTableGroupError(TemporaryTableGroupError error) {
        return switch (error) {
            case MEMBER_REQUIRED -> SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED;
            case MEMBER_DUPLICATE -> SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE;
            case MEMBER_UNAVAILABLE -> SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE;
            case CAPACITY_INSUFFICIENT -> SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT;
            case LOCK_CONFLICT -> SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_LOCK_CONFLICT;
            case PREASSIGNMENT_CONFLICT -> SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT;
            case GROUP_NAME_REQUIRED, GROUP_NAME_CONFLICT, GROUP_NOT_FOUND, GROUP_NOT_TEMPORARY, GROUP_NOT_DISSOLVABLE ->
                SeatingFromCalledQueueError.INVALID_COMMAND;
        };
    }

    private void validateTable(
        StoreScope scope,
        DiningTable table,
        PartySize partySize,
        SeatingFromCalledQueueError unavailableError
    ) {
        require(tableAvailabilityRule.evaluate(table), unavailableError);
        SeatingFromCalledQueueError capacityError = unavailableError == SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE
            ? SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE
            : SeatingFromCalledQueueError.TABLE_CAPACITY_INSUFFICIENT;
        require(tableCapacityRule.evaluate(partySize, table.capacity()), capacityError);
        requireNoLock(scope, RESOURCE_TABLE, table.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_TABLE, table.id().value())) {
            throw new ApplicationFailure(unavailableError);
        }
    }

    private void validateGroupMemberTable(StoreScope scope, DiningTable table) {
        require(tableAvailabilityRule.evaluate(table), SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE);
        requireNoLock(scope, RESOURCE_TABLE, table.id().value());
        if (seatingRepository.existsActiveResourceOccupancy(scope, RESOURCE_TABLE, table.id().value())) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE);
        }
    }

    private void requireNoLock(StoreScope scope, String resourceType, UUID resourceId) {
        require(
            tableLockRule.evaluate(tableLockRepository.existsActiveConflict(scope, resourceType, resourceId, OffsetDateTime.now(clock))),
            SeatingFromCalledQueueError.TABLE_LOCK_CONFLICT
        );
    }

    private void validatePreassignment(
        StoreScope scope,
        Optional<Reservation> reservation,
        ResourceSelection selection
    ) {
        if (reservation.isEmpty()) {
            return;
        }
        Reservation value = reservation.get();
        ReservationResourceAssignment ownAssignment = preassignmentRepository
            .findActiveAssignmentForReservation(scope, value.id().value())
            .orElse(null);
        if (ownAssignment != null && !matches(ownAssignment, selection)) {
            throw new ApplicationFailure(resourceUnavailableError(selection));
        }

        ReservationResourceAssignment resourceAssignment = preassignmentRepository
            .findActiveAssignmentForResource(scope, selection.resourceType(), selection.resourceId(), value.businessDate())
            .orElse(null);
        if (resourceAssignment != null && !resourceAssignment.reservationId().equals(value.id().value())) {
            throw new ApplicationFailure(resourceUnavailableError(selection));
        }
    }

    private static boolean matches(ReservationResourceAssignment assignment, ResourceSelection selection) {
        return assignment.resourceType().equals(selection.resourceType())
            && assignment.resourceId().equals(selection.resourceId());
    }

    private static SeatingFromCalledQueueError resourceUnavailableError(ResourceSelection selection) {
        if (selection.temporaryGroup()) {
            return SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT;
        }
        return selection.isTable()
            ? SeatingFromCalledQueueError.TABLE_NOT_AVAILABLE
            : SeatingFromCalledQueueError.TABLE_GROUP_INVALID;
    }

    private Seating createSeating(StoreScope scope, SeatCalledQueueTicketCommand command, QueueTicket queueTicket) {
        UUID seatingUuid = UUID.randomUUID();
        try {
            return seatingRepository.save(
                scope,
                new Seating(
                    new SeatingId(seatingUuid),
                    scope,
                    QUEUE_TICKET_SOURCE,
                    queueTicket.id().value(),
                    "S-" + seatingUuid.toString().substring(0, 8),
                    blankToNull(command.overrideReasonCode()),
                    seatingNote(command),
                    queueTicket.partySize(),
                    SeatingStatus.OCCUPIED
                )
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private SeatingResource createSeatingResource(StoreScope scope, Seating seating, ResourceSelection selection) {
        try {
            return seatingRepository.saveResource(
                scope,
                new SeatingResource(UUID.randomUUID(), scope, seating.id(), selection.resourceType(), selection.resourceId(), "active")
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.PERSISTENCE_ERROR, exception);
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
            throw new ApplicationFailure(SeatingFromCalledQueueError.ILLEGAL_STATE_TRANSITION);
        }
        TransitionResult<DiningTableStatus> occupyTransition = diningTableStateMachine.validateTransition(DiningTableStatus.LOCKED, DiningTableStatus.OCCUPIED);
        if (!occupyTransition.accepted()) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.ILLEGAL_STATE_TRANSITION);
        }
        try {
            return diningTableRepository.save(
                scope,
                new DiningTable(table.id(), table.scope(), table.areaId(), table.tableCode(), table.capacity(), DiningTableStatus.OCCUPIED, table.combinable())
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.PERSISTENCE_ERROR, exception);
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
            throw new ApplicationFailure(SeatingFromCalledQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueTicket saveSeated(StoreScope scope, QueueTicket queueTicket) {
        try {
            return queueTicketRepository.save(scope, queueTicket.seat());
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private WalkIn saveSeated(StoreScope scope, WalkIn walkIn) {
        try {
            return walkInRepository.save(scope, new WalkIn(walkIn.id(), walkIn.scope(), walkIn.partySize(), "seated"));
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        SeatCalledQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        Optional<WalkIn> walkIn,
        Seating seating,
        ResourceSelection selection,
        IdempotencyKey idempotencyKey
    ) {
        String metadata = metadata(queueTicket, reservation, walkIn, seating, selection, idempotencyKey, null);
        List<BusinessEvent> events = new ArrayList<>();
        events.add(newBusinessEvent(EVENT_QUEUE_TICKET_SEATED, QUEUE_TICKET_SOURCE, queueTicket.id().value(), command, metadata));
        reservation.ifPresent(value -> events.add(newBusinessEvent(EVENT_RESERVATION_SEATED, TARGET_RESERVATION, value.id().value(), command, metadata)));
        walkIn.ifPresent(value -> events.add(newBusinessEvent(EVENT_WALK_IN_SEATED, TARGET_WALK_IN, value.id().value(), command, metadata)));
        events.add(newBusinessEvent(EVENT_SEATING_CREATED, TARGET_SEATING, seating.id().value(), command, metadata));
        events.add(newBusinessEvent(EVENT_TABLE_OCCUPIED, selection.resourceType(), selection.resourceId(), command, metadata));
        List<UUID> ids = new ArrayList<>();
        for (BusinessEvent event : events) {
            require(
                businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
                SeatingFromCalledQueueError.BUSINESS_EVENT_WRITE_FAILED
            );
            try {
                ids.add(businessEventRepository.append(scope, event).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(SeatingFromCalledQueueError.BUSINESS_EVENT_WRITE_FAILED, exception);
            }
        }
        return ids;
    }

    private List<UUID> appendTransitionLogs(
        StoreScope scope,
        SeatCalledQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        Optional<WalkIn> walkIn,
        Seating seating,
        List<DiningTable> occupiedTables,
        IdempotencyKey idempotencyKey
    ) {
        List<StateTransitionLog> logs = new ArrayList<>();
        String metadata = metadata(queueTicket, reservation, walkIn, seating, null, idempotencyKey, null);
        logs.add(newTransition(QUEUE_TICKET_SOURCE, queueTicket.id().value(), "called", "seated", TRANSITION_QUEUE_TICKET_SEAT, command, metadata));
        reservation.ifPresent(value -> logs.add(newTransition(TARGET_RESERVATION, value.id().value(), "arrived", "seated", TRANSITION_RESERVATION_SEAT, command, metadata)));
        walkIn.ifPresent(value -> logs.add(newTransition(TARGET_WALK_IN, value.id().value(), "queued", "seated", TRANSITION_WALK_IN_SEAT_FROM_QUEUE, command, metadata)));
        logs.add(newTransition(TARGET_SEATING, seating.id().value(), "planned", "occupied", TRANSITION_SEATING_OCCUPY, command, metadata));
        for (DiningTable table : occupiedTables) {
            logs.add(newTransition(TARGET_DINING_TABLE, table.id().value(), "available", "occupied", TRANSITION_TABLE_OCCUPY, command, metadata));
        }
        List<UUID> ids = new ArrayList<>();
        for (StateTransitionLog log : logs) {
            require(
                stateTransitionRule.evaluate(log.targetType(), log.targetId(), log.toStatus(), log.transitionCode(), log.actorType()),
                SeatingFromCalledQueueError.STATE_TRANSITION_WRITE_FAILED
            );
            try {
                ids.add(stateTransitionLogRepository.append(scope, log).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(SeatingFromCalledQueueError.STATE_TRANSITION_WRITE_FAILED, exception);
            }
        }
        return ids;
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        SeatCalledQueueTicketCommand command,
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        Optional<WalkIn> walkIn,
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
            QUEUE_TICKET_SOURCE,
            queueTicket.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(queueTicket, reservation, walkIn, seating, selection, idempotencyKey, extra)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), SeatingFromCalledQueueError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        SeatCalledQueueTicketCommand command,
        IdempotencyKey idempotencyKey,
        SeatingFromCalledQueueError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_SEAT_FAILED,
                    QUEUE_TICKET_SOURCE,
                    command.queueTicketId(),
                    source(command),
                    command.actorType(),
                    command.actorId(),
                    """
                        {"failureReason":"%s","queueTicketId":"%s","tableId":%s,"tableGroupId":%s,"idempotencyKey":"%s"}
                        """.formatted(
                        error.code(),
                        command.queueTicketId(),
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
        SeatCalledQueueTicketCommand command,
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
        SeatCalledQueueTicketCommand command,
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
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        Seating seating,
        SeatingResource seatingResource,
        String tableStatus,
        List<String> groupMemberStatuses,
        boolean alreadySeated
    ) {
        String snapshot = snapshot(queueTicket, reservation, seating, seatingResource, tableStatus, groupMemberStatuses, alreadySeated);
        IdempotencyRecord completionPayload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            TARGET_SEATING,
            seating.id().value(),
            snapshot
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, TARGET_SEATING);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(SeatingFromCalledQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private SeatingFromCalledQueueResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            SeatingFromCalledQueueError error = SeatingFromCalledQueueError.fromCode(decision.violationCode());
            if (error == SeatingFromCalledQueueError.IDEMPOTENCY_IN_PROGRESS) {
                return SeatingFromCalledQueueResult.retryLater(error);
            }
            return SeatingFromCalledQueueResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return SeatingFromCalledQueueResult.failure(SeatingFromCalledQueueError.IDEMPOTENCY_CONFLICT);
            }
        }
        return SeatingFromCalledQueueResult.failure(SeatingFromCalledQueueError.IDEMPOTENCY_CONFLICT);
    }

    private SeatingFromCalledQueueResult replay(String snapshot) {
        return SeatingFromCalledQueueResult.replay(
            UUID.fromString(extract(snapshot, "queueTicketId")),
            Integer.parseInt(extractNumber(snapshot, "queueTicketNumber")),
            extract(snapshot, "queueTicketStatus"),
            extractNullableUuid(snapshot, "reservationId"),
            extractNullableString(snapshot, "reservationCode"),
            extractNullableString(snapshot, "reservationStatus"),
            UUID.fromString(extract(snapshot, "seatingId")),
            extract(snapshot, "resourceType"),
            UUID.fromString(extract(snapshot, "resourceId")),
            Integer.parseInt(extractNumber(snapshot, "partySizeSnapshot")),
            extract(snapshot, "seatingStatus"),
            extract(snapshot, "seatingResourceStatus"),
            extractNullableString(snapshot, "tableStatus"),
            extractStringArray(snapshot, "groupMemberStatuses"),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadySeated"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, SeatingFromCalledQueueError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static SeatingFromCalledQueueError validateCommand(SeatCalledQueueTicketCommand command) {
        if (command == null) {
            return SeatingFromCalledQueueError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return SeatingFromCalledQueueError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.queueTicketId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return SeatingFromCalledQueueError.INVALID_COMMAND;
        }
        boolean hasTemporaryTables = command.temporaryTableIds() != null && !command.temporaryTableIds().isEmpty();
        int selectedTargets = (command.tableId() == null ? 0 : 1)
            + (command.tableGroupId() == null ? 0 : 1)
            + (hasTemporaryTables ? 1 : 0);
        if (selectedTargets > 1) {
            return SeatingFromCalledQueueError.RESOURCE_SELECTION_CONFLICT;
        }
        if (selectedTargets == 0) {
            return SeatingFromCalledQueueError.RESOURCE_SELECTION_REQUIRED;
        }
        if (hasTemporaryTables && command.temporaryTableIds().size() < 2) {
            return SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED;
        }
        if (hasTemporaryTables && command.temporaryTableIds().stream().distinct().count() != command.temporaryTableIds().size()) {
            return SeatingFromCalledQueueError.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE;
        }
        return null;
    }

    private static SeatingFromCalledQueueError fromRuleCode(String code, SeatingFromCalledQueueError fallback) {
        if (
            fallback == SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE
                && ("table_resource_unavailable".equals(code) || "party_size_outside_capacity".equals(code))
        ) {
            return fallback;
        }
        if (
            fallback == SeatingFromCalledQueueError.TABLE_GROUP_CAPACITY_INSUFFICIENT
                && "party_size_outside_capacity".equals(code)
        ) {
            return fallback;
        }
        SeatingFromCalledQueueError error = SeatingFromCalledQueueError.fromCode(code);
        return error == SeatingFromCalledQueueError.INVALID_COMMAND ? fallback : error;
    }

    private static void require(RuleDecision decision, SeatingFromCalledQueueError fallback) {
        if (!decision.accepted()) {
            throw new ApplicationFailure(fromRuleCode(decision.violationCode(), fallback));
        }
    }

    private static String snapshot(
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        Seating seating,
        SeatingResource seatingResource,
        String tableStatus,
        List<String> groupMemberStatuses,
        boolean alreadySeated
    ) {
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"seated","reservationId":%s,"reservationCode":%s,"reservationStatus":%s,"seatingId":"%s","resourceType":"%s","resourceId":"%s","partySizeSnapshot":%d,"seatingStatus":"%s","seatingResourceStatus":"%s","tableStatus":%s,"groupMemberStatuses":%s,"alreadySeated":%s}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            jsonNullable(reservation.map(value -> value.id().value()).orElse(null)),
            jsonNullable(reservation.map(value -> value.reservationCode().value()).orElse(null)),
            jsonNullable(reservation.map(value -> value.status().code()).orElse(null)),
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
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        Seating seating,
        ResourceSelection selection,
        IdempotencyKey idempotencyKey,
        String extraJsonWithoutLeadingBrace
    ) {
        return metadata(queueTicket, reservation, Optional.empty(), seating, selection, idempotencyKey, extraJsonWithoutLeadingBrace);
    }

    private static String metadata(
        QueueTicket queueTicket,
        Optional<Reservation> reservation,
        Optional<WalkIn> walkIn,
        Seating seating,
        ResourceSelection selection,
        IdempotencyKey idempotencyKey,
        String extraJsonWithoutLeadingBrace
    ) {
        String resourceType = selection == null ? null : selection.resourceType();
        UUID resourceId = selection == null ? null : selection.resourceId();
        String extra = hasText(extraJsonWithoutLeadingBrace) ? extraJsonWithoutLeadingBrace : "";
        return """
            {"queueTicketId":"%s","queueTicketNumber":%d,"reservationId":%s,"reservationCode":%s,"walkInId":%s,"seatingId":"%s","resourceType":%s,"resourceId":%s,"beforeQueueTicketStatus":"called","afterQueueTicketStatus":"seated","beforeReservationStatus":%s,"afterReservationStatus":%s,"beforeWalkInStatus":%s,"afterWalkInStatus":%s,"idempotencyKey":"%s"%s}
            """.formatted(
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            jsonNullable(reservation.map(value -> value.id().value()).orElse(null)),
            jsonNullable(reservation.map(value -> value.reservationCode().value()).orElse(null)),
            jsonNullable(walkIn.map(value -> value.id().value()).orElse(null)),
            seating.id().value(),
            jsonNullable(resourceType),
            jsonNullable(resourceId),
            jsonNullable(reservation.map(value -> ReservationStatus.ARRIVED.code()).orElse(null)),
            jsonNullable(reservation.map(value -> ReservationStatus.SEATED.code()).orElse(null)),
            jsonNullable(walkIn.map(value -> "queued").orElse(null)),
            jsonNullable(walkIn.map(value -> "seated").orElse(null)),
            escape(idempotencyKey.value()),
            extra
        ).trim();
    }

    private static String seatingNote(SeatCalledQueueTicketCommand command) {
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

    private static UUID extractNullableUuid(String json, String key) {
        String value = extractNullableString(json, key);
        return value == null ? null : UUID.fromString(value);
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

    private static String source(SeatCalledQueueTicketCommand command) {
        return OperationSource.fromActorType(command == null ? null : command.actorType());
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
        List<DiningTable> memberTables,
        boolean temporaryGroup
    ) {
        private boolean isTable() {
            return RESOURCE_TABLE.equals(resourceType);
        }
    }

    private enum NoopReservationPreassignmentRepository implements ReservationPreassignmentRepositoryPort {
        INSTANCE;

        @Override
        public boolean existsActiveResourceConflict(
            StoreScope scope,
            String resourceType,
            UUID resourceId,
            com.rpb.reservation.common.time.BusinessDate businessDate,
            com.rpb.reservation.common.time.TimeRange timeRange
        ) {
            return false;
        }

        @Override
        public Set<ReservationResourceAssignment> findActiveResourceAssignmentsForDate(
            StoreScope scope,
            com.rpb.reservation.common.time.BusinessDate businessDate
        ) {
            return Set.of();
        }

        @Override
        public Optional<ReservationResourceAssignment> findActiveAssignmentForReservation(
            StoreScope scope,
            UUID reservationId
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<ReservationResourceAssignment> findActiveAssignmentForResource(
            StoreScope scope,
            String resourceType,
            UUID resourceId,
            com.rpb.reservation.common.time.BusinessDate businessDate
        ) {
            return Optional.empty();
        }

        @Override
        public ReservationPreassignment save(StoreScope scope, ReservationPreassignment preassignment) {
            return preassignment;
        }
    }

    private enum NoopWalkInRepository implements WalkInRepositoryPort {
        INSTANCE;

        @Override
        public Optional<WalkIn> findById(StoreScope scope, WalkInId walkInId) {
            return Optional.empty();
        }

        @Override
        public Optional<WalkIn> findByCode(StoreScope scope, String walkInCode) {
            return Optional.empty();
        }

        @Override
        public List<WalkIn> findArrivals(StoreScope scope, BusinessDate businessDate, String statusCode) {
            return List.of();
        }

        @Override
        public WalkIn save(StoreScope scope, WalkIn walkIn) {
            return walkIn;
        }
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final SeatingFromCalledQueueError error;
        private final boolean retryLater;

        private ApplicationFailure(SeatingFromCalledQueueError error) {
            this(error, false, null);
        }

        private ApplicationFailure(SeatingFromCalledQueueError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(SeatingFromCalledQueueError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(SeatingFromCalledQueueError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private SeatingFromCalledQueueError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
