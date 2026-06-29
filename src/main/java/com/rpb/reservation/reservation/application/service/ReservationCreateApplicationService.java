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
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.OperationSource;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.reservation.application.ReservationCreateError;
import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.rule.ReservationAvailabilityRule;
import com.rpb.reservation.reservation.application.rule.ReservationCodePolicy;
import com.rpb.reservation.reservation.application.rule.ReservationDuplicateRule;
import com.rpb.reservation.reservation.application.rule.ReservationHoldPolicy;
import com.rpb.reservation.reservation.application.rule.ReservationTimeRangeRule;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.application.port.out.StorePolicyRepositoryPort;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.domain.StorePolicy;
import com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort;
import com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.domain.TableGroup;
import com.rpb.reservation.table.rule.DefaultTableAvailabilityRule;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationCreateApplicationService {

    private static final String ACTION = "create_reservation";
    private static final String TARGET_RESERVATION = "reservation";
    private static final int V1_FALLBACK_CAPACITY_LIMIT = 50;
    private static final int RESERVATION_CODE_MAX_ATTEMPTS = 5;

    private final StoreRepositoryPort storeRepository;
    private final StorePolicyRepositoryPort storePolicyRepository;
    private final CustomerRepositoryPort customerRepository;
    private final ReservationRepositoryPort reservationRepository;
    private final ReservationPreassignmentRepositoryPort preassignmentRepository;
    private final DiningTableRepositoryPort diningTableRepository;
    private final TableGroupRepositoryPort tableGroupRepository;
    private final BusinessEventRepositoryPort businessEventRepository;
    private final StateTransitionLogRepositoryPort stateTransitionLogRepository;
    private final AuditLogRepositoryPort auditLogRepository;
    private final IdempotencyRepositoryPort idempotencyRepository;
    private final Clock clock;
    private final ReservationCodePolicy reservationCodePolicy;
    private final ReservationMealPeriodScheduleService reservationMealPeriodScheduleService;
    private final DefaultStoreAccessPolicy storeAccessPolicy = new DefaultStoreAccessPolicy();
    private final DefaultAuditRule auditRule = new DefaultAuditRule();
    private final DefaultBusinessEventRule businessEventRule = new DefaultBusinessEventRule();
    private final DefaultStateTransitionRule stateTransitionRule = new DefaultStateTransitionRule();
    private final DefaultIdempotencyRule idempotencyRule = new DefaultIdempotencyRule();
    private final ReservationHoldPolicy reservationHoldPolicy = new ReservationHoldPolicy();
    private final ReservationTimeRangeRule reservationTimeRangeRule = new ReservationTimeRangeRule();
    private final ReservationAvailabilityRule reservationAvailabilityRule = new ReservationAvailabilityRule();
    private final ReservationDuplicateRule reservationDuplicateRule = new ReservationDuplicateRule();
    private final DefaultTableAvailabilityRule tableAvailabilityRule = new DefaultTableAvailabilityRule();

    @Autowired
    public ReservationCreateApplicationService(
        StoreRepositoryPort storeRepository,
        StorePolicyRepositoryPort storePolicyRepository,
        CustomerRepositoryPort customerRepository,
        ReservationRepositoryPort reservationRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock,
        ReservationMealPeriodRepositoryPort mealPeriodRepository
    ) {
        this(
            storeRepository,
            storePolicyRepository,
            customerRepository,
            reservationRepository,
            preassignmentRepository,
            diningTableRepository,
            tableGroupRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            clock,
            new ReservationCodePolicy(),
            mealPeriodRepository
        );
    }

    public ReservationCreateApplicationService(
        StoreRepositoryPort storeRepository,
        StorePolicyRepositoryPort storePolicyRepository,
        CustomerRepositoryPort customerRepository,
        ReservationRepositoryPort reservationRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock,
        IntSupplier reservationCodeSequenceSupplier
    ) {
        this(
            storeRepository,
            storePolicyRepository,
            customerRepository,
            reservationRepository,
            preassignmentRepository,
            diningTableRepository,
            tableGroupRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            clock,
            new ReservationCodePolicy(reservationCodeSequenceSupplier),
            ReservationMealPeriodRepositoryPort.platformDefault()
        );
    }

    private ReservationCreateApplicationService(
        StoreRepositoryPort storeRepository,
        StorePolicyRepositoryPort storePolicyRepository,
        CustomerRepositoryPort customerRepository,
        ReservationRepositoryPort reservationRepository,
        ReservationPreassignmentRepositoryPort preassignmentRepository,
        DiningTableRepositoryPort diningTableRepository,
        TableGroupRepositoryPort tableGroupRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock,
        ReservationCodePolicy reservationCodePolicy,
        ReservationMealPeriodRepositoryPort mealPeriodRepository
    ) {
        this.storeRepository = storeRepository;
        this.storePolicyRepository = storePolicyRepository;
        this.customerRepository = customerRepository;
        this.reservationRepository = reservationRepository;
        this.preassignmentRepository = preassignmentRepository;
        this.diningTableRepository = diningTableRepository;
        this.tableGroupRepository = tableGroupRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
        this.reservationCodePolicy = reservationCodePolicy;
        this.reservationMealPeriodScheduleService = new ReservationMealPeriodScheduleService(mealPeriodRepository);
    }

    @Transactional
    public ReservationCreateResult createReservation(CreateReservationCommand command) {
        ReservationCreateError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return ReservationCreateResult.failure(preValidationError);
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
            return ReservationCreateResult.failure(ReservationCreateError.REPOSITORY_SAVE_FAILED);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? ReservationCreateResult.retryLater(failure.error())
                : ReservationCreateResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationCreateError.REPOSITORY_SAVE_FAILED);
            appendFailureAudit(scope, command, started.idempotencyKey(), ReservationCreateError.REPOSITORY_SAVE_FAILED);
            return ReservationCreateResult.failure(ReservationCreateError.REPOSITORY_SAVE_FAILED);
        }
    }

    public static String requestHash(CreateReservationCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.partySize()),
            value(command.reservedStartAt()),
            value(command.reservedEndAt()),
            value(command.businessDate()),
            value(command.customerId()),
            normalize(command.customerName()),
            normalize(command.customerNickname()),
            normalize(command.phoneE164()),
            normalize(command.note()),
            normalize(command.actorType()),
            normalize(command.reservationCode()),
            normalize(command.source()),
            normalize(command.reasonCode()),
            value(command.tableId()),
            value(command.tableGroupId())
        );
        return sha256(normalized);
    }

    private ReservationCreateResult execute(CreateReservationCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationCreateError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(ReservationCreateError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), ReservationCreateError.STORE_ACCESS_DENIED);

        Optional<StorePolicy> policy = storePolicyRepository.findByStoreScope(scope);
        Instant reservedStartAt = command.reservedStartAt();
        Instant reservedEndAt = command.reservedEndAt() == null
            ? reservationHoldPolicy.deriveReservedEndAt(reservedStartAt, policy)
            : command.reservedEndAt();
        Instant now = Instant.now(clock);
        ReservationCreateError timeRangeError = reservationTimeRangeRule.validate(command.partySize(), reservedStartAt, reservedEndAt, now);
        if (timeRangeError != null) {
            throw new ApplicationFailure(timeRangeError);
        }

        PartySize partySize = new PartySize(command.partySize());
        BusinessDate businessDate = command.businessDate() == null
            ? businessDate(store, reservedStartAt)
            : new BusinessDate(command.businessDate());
        if (!reservationMealPeriodScheduleService.isSelectableSlot(
            scope,
            store.timezone(),
            businessDate.value(),
            reservedStartAt,
            now
        )) {
            throw new ApplicationFailure(ReservationCreateError.RESERVATION_TIME_SLOT_UNAVAILABLE);
        }
        TimeRange timeRange = new TimeRange(reservedStartAt, reservedEndAt);
        Instant holdUntilAt = reservationHoldPolicy.holdUntilAt(reservedStartAt, policy);
        Customer customer = resolveCustomer(command, scope.tenantScope());

        boolean duplicateActive = reservationRepository.existsActiveDuplicate(scope, customer.id(), timeRange);
        if (!reservationDuplicateRule.allows(duplicateActive)) {
            throw new ApplicationFailure(ReservationCreateError.RESERVATION_DUPLICATE_ACTIVE);
        }

        int capacityUsage = reservationRepository.findActiveCapacityUsage(scope, businessDate, timeRange);
        if (!reservationAvailabilityRule.canAccept(capacityUsage, partySize.value(), V1_FALLBACK_CAPACITY_LIMIT)) {
            throw new ApplicationFailure(ReservationCreateError.RESERVATION_CAPACITY_INSUFFICIENT);
        }

        ResourceSelection resourceSelection = resolveResourceSelection(command, scope, partySize, businessDate, timeRange);

        ReservationCode reservationCode = resolveReservationCode(command, businessDate, scope);
        Instant persistedAt = Instant.now(clock);
        Reservation reservation = saveReservation(
            scope,
            command,
            customer,
            reservationCode,
            partySize,
            businessDate,
            reservedStartAt,
            reservedEndAt,
            holdUntilAt,
            persistedAt
        );
        savePreassignment(scope, reservation, resourceSelection);

        List<UUID> eventIds = appendBusinessEvents(scope, command, reservation, started.idempotencyKey());
        List<UUID> transitionIds = appendTransitionLogs(scope, command, reservation, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, reservation, started.idempotencyKey());
        IdempotencyRecord completed = completeIdempotency(scope, started, reservation);

        return ReservationCreateResult.success(
            reservation.id().value(),
            reservation.customerId().value(),
            reservation.reservationCode().value(),
            reservation.partySize().value(),
            reservation.businessDate().value(),
            reservation.reservedStartAt(),
            reservation.reservedEndAt(),
            reservation.holdUntilAt(),
            reservation.status().code(),
            completed.status().code(),
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private Customer resolveCustomer(CreateReservationCommand command, TenantScope tenantScope) {
        E164Phone phone = parsePhone(command.phoneE164());
        if (command.customerId() != null) {
            Customer customer = customerRepository.findById(tenantScope, new CustomerId(command.customerId()))
                .orElseThrow(() -> new ApplicationFailure(ReservationCreateError.CUSTOMER_NOT_FOUND));
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
                "temporary",
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
        CreateReservationCommand command,
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

    private ReservationCode resolveReservationCode(CreateReservationCommand command, BusinessDate businessDate, StoreScope scope) {
        if (hasText(command.reservationCode())) {
            ReservationCode requested = new ReservationCode(command.reservationCode().trim());
            if (reservationRepository.existsByReservationCode(scope, requested)) {
                throw new ApplicationFailure(ReservationCreateError.RESERVATION_CODE_CONFLICT);
            }
            return requested;
        }

        for (int attempt = 0; attempt < RESERVATION_CODE_MAX_ATTEMPTS; attempt++) {
            ReservationCode generated = reservationCodePolicy.next(businessDate.value());
            if (!reservationRepository.existsByReservationCode(scope, generated)) {
                return generated;
            }
        }
        throw new ApplicationFailure(ReservationCreateError.RESERVATION_CODE_CONFLICT);
    }

    private Reservation saveReservation(
        StoreScope scope,
        CreateReservationCommand command,
        Customer customer,
        ReservationCode reservationCode,
        PartySize partySize,
        BusinessDate businessDate,
        Instant reservedStartAt,
        Instant reservedEndAt,
        Instant holdUntilAt,
        Instant persistedAt
    ) {
        try {
            return reservationRepository.save(
                scope,
                new Reservation(
                    new ReservationId(UUID.randomUUID()),
                    scope,
                    customer.id(),
                    reservationCode,
                    partySize,
                    businessDate,
                    reservedStartAt,
                    reservedEndAt,
                    holdUntilAt,
                    ReservationStatus.CONFIRMED,
                    source(command),
                    null,
                    null,
                    blankToNull(command.note()),
                    persistedAt,
                    persistedAt,
                    null
                )
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCreateError.REPOSITORY_SAVE_FAILED);
        }
    }

    private ResourceSelection resolveResourceSelection(
        CreateReservationCommand command,
        StoreScope scope,
        PartySize partySize,
        BusinessDate businessDate,
        TimeRange timeRange
    ) {
        if (command.tableId() != null && command.tableGroupId() != null) {
            throw new ApplicationFailure(ReservationCreateError.RESOURCE_SELECTION_CONFLICT);
        }
        if (command.tableId() != null) {
            DiningTable table = diningTableRepository.findById(scope, new TableId(command.tableId()))
                .orElseThrow(() -> new ApplicationFailure(ReservationCreateError.TABLE_NOT_FOUND));
            if (table.status() == DiningTableStatus.INACTIVE) {
                throw new ApplicationFailure(ReservationCreateError.TABLE_NOT_AVAILABLE);
            }
            if (!table.capacity().includes(partySize)) {
                throw new ApplicationFailure(ReservationCreateError.TABLE_CAPACITY_INSUFFICIENT);
            }
            if (hasActivePreassignmentConflict(scope, "dining_table", command.tableId(), businessDate, timeRange)) {
                throw new ApplicationFailure(ReservationCreateError.TABLE_NOT_AVAILABLE);
            }
            return new ResourceSelection("dining_table", command.tableId());
        }
        if (command.tableGroupId() != null) {
            TableGroup group = tableGroupRepository.findById(scope, new TableGroupId(command.tableGroupId()))
                .orElseThrow(() -> new ApplicationFailure(ReservationCreateError.TABLE_GROUP_NOT_FOUND));
            require(tableAvailabilityRule.evaluate(group), ReservationCreateError.TABLE_GROUP_INVALID);
            if (!group.capacity().includes(partySize)) {
                throw new ApplicationFailure(ReservationCreateError.TABLE_GROUP_CAPACITY_INSUFFICIENT);
            }
            if (hasActivePreassignmentConflict(scope, "table_group", command.tableGroupId(), businessDate, timeRange)) {
                throw new ApplicationFailure(ReservationCreateError.TABLE_GROUP_INVALID);
            }
            return new ResourceSelection("table_group", command.tableGroupId());
        }
        return null;
    }

    private boolean hasActivePreassignmentConflict(
        StoreScope scope,
        String resourceType,
        UUID resourceId,
        BusinessDate businessDate,
        TimeRange timeRange
    ) {
        try {
            return preassignmentRepository.existsActiveResourceConflict(
                scope,
                resourceType,
                resourceId,
                businessDate,
                timeRange
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCreateError.PERSISTENCE_ERROR);
        }
    }

    private void savePreassignment(
        StoreScope scope,
        Reservation reservation,
        ResourceSelection resourceSelection
    ) {
        if (resourceSelection == null) {
            return;
        }
        try {
            preassignmentRepository.save(
                scope,
                new ReservationPreassignment(
                    UUID.randomUUID(),
                    scope,
                    reservation.id(),
                    resourceSelection.resourceType(),
                    resourceSelection.resourceId(),
                    "active"
                )
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCreateError.REPOSITORY_SAVE_FAILED);
        }
    }

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        CreateReservationCommand command,
        Reservation reservation,
        IdempotencyKey idempotencyKey
    ) {
        List<BusinessEvent> events = List.of(
            newBusinessEvent("reservation.created", reservation, command, idempotencyKey),
            newBusinessEvent("reservation.confirmed", reservation, command, idempotencyKey)
        );
        List<UUID> ids = new ArrayList<>();
        for (BusinessEvent event : events) {
            require(businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()), ReservationCreateError.BUSINESS_EVENT_WRITE_FAILED);
            try {
                ids.add(businessEventRepository.append(scope, event).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(ReservationCreateError.BUSINESS_EVENT_WRITE_FAILED);
            }
        }
        return ids;
    }

    private BusinessEvent newBusinessEvent(
        String eventType,
        Reservation reservation,
        CreateReservationCommand command,
        IdempotencyKey idempotencyKey
    ) {
        return new BusinessEvent(
            UUID.randomUUID(),
            eventType,
            TARGET_RESERVATION,
            reservation.id().value(),
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, idempotencyKey)
        );
    }

    private List<UUID> appendTransitionLogs(
        StoreScope scope,
        CreateReservationCommand command,
        Reservation reservation,
        IdempotencyKey idempotencyKey
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_RESERVATION,
            reservation.id().value(),
            "none",
            ReservationStatus.CONFIRMED.code(),
            "reservation.confirm",
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, command, idempotencyKey)
        );
        require(
            stateTransitionRule.evaluate(
                transition.targetType(),
                transition.targetId(),
                transition.toStatus(),
                transition.transitionCode(),
                transition.actorType()
            ),
            ReservationCreateError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return List.of(stateTransitionLogRepository.append(scope, transition).id());
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCreateError.STATE_TRANSITION_WRITE_FAILED);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        CreateReservationCommand command,
        Reservation reservation,
        IdempotencyKey idempotencyKey
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            "reservation.create",
            TARGET_RESERVATION,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, command, idempotencyKey)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), ReservationCreateError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationCreateError.AUDIT_WRITE_FAILED);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        CreateReservationCommand command,
        IdempotencyKey idempotencyKey,
        ReservationCreateError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    "reservation.create.failed",
                    "reservation_create",
                    UUID.randomUUID(),
                    source(command),
                    command.actorType(),
                    command.actorId(),
                    """
                        {"failureReason":"%s","reasonCode":%s,"idempotencyKey":"%s"}
                        """.formatted(error.code(), jsonNullable(command.reasonCode()), escape(idempotencyKey.value())).trim()
                )
            );
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private IdempotencyRecord completeIdempotency(StoreScope scope, IdempotencyRecord started, Reservation reservation) {
        String snapshot = snapshot(reservation);
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

    private ReservationCreateResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationCreateError error = ReservationCreateError.fromCode(decision.violationCode());
            if (error == ReservationCreateError.COMMAND_IN_PROGRESS) {
                return ReservationCreateResult.retryLater(error);
            }
            return ReservationCreateResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return ReservationCreateResult.failure(ReservationCreateError.IDEMPOTENCY_CONFLICT);
            }
        }
        return ReservationCreateResult.failure(ReservationCreateError.IDEMPOTENCY_CONFLICT);
    }

    private ReservationCreateResult replay(String snapshot) {
        return ReservationCreateResult.replay(
            UUID.fromString(extract(snapshot, "reservationId")),
            UUID.fromString(extract(snapshot, "customerId")),
            extract(snapshot, "reservationCode"),
            Integer.parseInt(extract(snapshot, "partySize")),
            LocalDate.parse(extract(snapshot, "businessDate")),
            Instant.parse(extract(snapshot, "reservedStartAt")),
            Instant.parse(extract(snapshot, "reservedEndAt")),
            Instant.parse(extract(snapshot, "holdUntilAt")),
            extract(snapshot, "status")
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationCreateError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static ReservationCreateError validateCommand(CreateReservationCommand command) {
        if (command == null) {
            return ReservationCreateError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationCreateError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationCreateError.INVALID_COMMAND;
        }
        if (command.partySize() == null || command.partySize() <= 0) {
            return ReservationCreateError.INVALID_PARTY_SIZE;
        }
        if (command.reservedStartAt() == null) {
            return ReservationCreateError.INVALID_TIME_RANGE;
        }
        if (command.tableId() != null && command.tableGroupId() != null) {
            return ReservationCreateError.RESOURCE_SELECTION_CONFLICT;
        }
        return null;
    }

    private static E164Phone parsePhone(String phoneE164) {
        try {
            return hasText(phoneE164) ? new E164Phone(phoneE164.trim()) : E164Phone.empty();
        } catch (IllegalArgumentException exception) {
            throw new ApplicationFailure(ReservationCreateError.INVALID_PHONE_E164);
        }
    }

    private static BusinessDate businessDate(Store store, Instant reservedStartAt) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(store.timezone());
        } catch (RuntimeException exception) {
            zoneId = ZoneOffset.UTC;
        }
        return new BusinessDate(reservedStartAt.atZone(zoneId).toLocalDate());
    }

    private static void require(RuleDecision decision, ReservationCreateError fallback) {
        if (!decision.accepted()) {
            ReservationCreateError error = ReservationCreateError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == ReservationCreateError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String source(CreateReservationCommand command) {
        return OperationSource.fromSourceOrActor(
            command == null ? null : command.source(),
            command == null ? null : command.actorType()
        );
    }

    private static String snapshot(Reservation reservation) {
        return """
            {"reservationId":"%s","customerId":"%s","reservationCode":"%s","partySize":%d,"businessDate":"%s","reservedStartAt":"%s","reservedEndAt":"%s","holdUntilAt":"%s","status":"%s"}
            """.formatted(
            reservation.id().value(),
            reservation.customerId().value(),
            reservation.reservationCode().value(),
            reservation.partySize().value(),
            reservation.businessDate().value(),
            reservation.reservedStartAt(),
            reservation.reservedEndAt(),
            reservation.holdUntilAt(),
            reservation.status().code()
        ).trim();
    }

    private static String metadata(Reservation reservation, CreateReservationCommand command, IdempotencyKey idempotencyKey) {
        return """
            {"reservationId":"%s","customerId":"%s","reservationCode":"%s","partySize":%d,"businessDate":"%s","reservedStartAt":"%s","reservedEndAt":"%s","holdUntilAt":"%s","status":"%s","source":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            reservation.id().value(),
            reservation.customerId().value(),
            reservation.reservationCode().value(),
            reservation.partySize().value(),
            reservation.businessDate().value(),
            reservation.reservedStartAt(),
            reservation.reservedEndAt(),
            reservation.holdUntilAt(),
            reservation.status().code(),
            jsonNullable(source(command)),
            jsonNullable(command.reasonCode()),
            jsonNullable(command.note()),
            escape(idempotencyKey.value())
        ).trim();
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

    private static String blankToNull(String value) {
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ResourceSelection(String resourceType, UUID resourceId) {
    }

    private static final class ApplicationFailure extends RuntimeException {
        private final ReservationCreateError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationCreateError error) {
            this(error, false);
        }

        private ApplicationFailure(ReservationCreateError error, boolean retryLater) {
            super(error.code());
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationCreateError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
