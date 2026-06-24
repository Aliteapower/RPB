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
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort;
import com.rpb.reservation.idempotency.domain.IdempotencyRecord;
import com.rpb.reservation.idempotency.rule.DefaultIdempotencyRule;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.queue.application.port.out.QueueGroupRepositoryPort;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.domain.QueueGroup;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.policy.QueueTicketNumberConflictException;
import com.rpb.reservation.queue.policy.QueueTicketNumberPolicy;
import com.rpb.reservation.queue.rule.QueueGroupSelectionRule;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import com.rpb.reservation.queue.value.QueueTicketNumber;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.application.WalkInQueueError;
import com.rpb.reservation.walkin.application.WalkInQueueResult;
import com.rpb.reservation.walkin.application.command.QueueWalkInCommand;
import com.rpb.reservation.walkin.application.port.out.WalkInRepositoryPort;
import com.rpb.reservation.walkin.domain.WalkIn;
import com.rpb.reservation.walkin.value.WalkInId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
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
public class WalkInQueueApplicationService {

    private static final String ACTION = "queue_walk_in";
    private static final String TARGET_WALK_IN = "walk_in";
    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_WALK_IN_QUEUED = "walk_in.queued";
    private static final String EVENT_QUEUE_TICKET_CREATED = "queue_ticket.created";
    private static final String OPERATION_QUEUE = "walk_in.queue";
    private static final String OPERATION_QUEUE_FAILED = "walk_in.queue.failed";
    private static final String TRANSITION_WALK_IN_QUEUE = "walk_in.queue";
    private static final String TRANSITION_QUEUE_TICKET_CREATE = "queue_ticket.create";

    private final StoreRepositoryPort storeRepository;
    private final CustomerRepositoryPort customerRepository;
    private final WalkInRepositoryPort walkInRepository;
    private final QueueGroupRepositoryPort queueGroupRepository;
    private final QueueTicketRepositoryPort queueTicketRepository;
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
    private final QueueGroupSelectionRule queueGroupSelectionRule = new QueueGroupSelectionRule();
    private final QueueTicketNumberPolicy queueTicketNumberPolicy = new QueueTicketNumberPolicy();

    @Autowired
    public WalkInQueueApplicationService(
        StoreRepositoryPort storeRepository,
        CustomerRepositoryPort customerRepository,
        WalkInRepositoryPort walkInRepository,
        QueueGroupRepositoryPort queueGroupRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this(
            storeRepository,
            customerRepository,
            walkInRepository,
            queueGroupRepository,
            queueTicketRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            Clock.systemUTC()
        );
    }

    public WalkInQueueApplicationService(
        StoreRepositoryPort storeRepository,
        CustomerRepositoryPort customerRepository,
        WalkInRepositoryPort walkInRepository,
        QueueGroupRepositoryPort queueGroupRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.customerRepository = customerRepository;
        this.walkInRepository = walkInRepository;
        this.queueGroupRepository = queueGroupRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional
    public WalkInQueueResult queueWalkIn(QueueWalkInCommand command) {
        WalkInQueueError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return WalkInQueueResult.failure(preValidationError);
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
            return WalkInQueueResult.failure(WalkInQueueError.PERSISTENCE_ERROR);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? WalkInQueueResult.retryLater(failure.error())
                : WalkInQueueResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, WalkInQueueError.PERSISTENCE_ERROR);
            appendFailureAudit(scope, command, started.idempotencyKey(), WalkInQueueError.PERSISTENCE_ERROR);
            return WalkInQueueResult.failure(WalkInQueueError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(QueueWalkInCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.partySize()),
            value(command.customerId()),
            normalize(command.customerName()),
            normalize(command.customerNickname()),
            normalize(command.phoneE164()),
            normalize(command.note()),
            value(command.actorId()),
            normalize(command.actorType())
        );
        return sha256(normalized);
    }

    private WalkInQueueResult execute(QueueWalkInCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(WalkInQueueError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(WalkInQueueError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), WalkInQueueError.STORE_ACCESS_DENIED);

        PartySize partySize = new PartySize(command.partySize());
        Customer customer = resolveCustomer(command, scope.tenantScope());
        BusinessDate businessDate = businessDate(store);
        QueueGroup queueGroup = resolveQueueGroup(scope, partySize);
        List<QueueTicket> activeQueue = activeQueue(scope, queueGroup, businessDate);
        QueueTicketNumber ticketNumber = queueTicketNumberPolicy.nextTicketNumber(activeQueue);
        int queuePosition = queueTicketNumberPolicy.nextQueuePosition(activeQueue);

        WalkIn walkIn = saveWalkIn(scope, new WalkIn(new WalkInId(UUID.randomUUID()), scope, partySize, "queued"));
        QueueTicket queueTicket = saveQueueTicket(scope, new QueueTicket(
            new QueueTicketId(UUID.randomUUID()),
            scope,
            queueGroup.id(),
            customer == null ? null : customer.id(),
            null,
            walkIn.id().value(),
            ticketNumber,
            partySize,
            businessDate,
            QueueTicketStatus.WAITING,
            queuePosition,
            blankToNull(command.note())
        ));

        List<UUID> eventIds = appendBusinessEvents(scope, command, walkIn, queueTicket, queueGroup, started.idempotencyKey());
        List<UUID> transitionIds = appendTransitionLogs(scope, command, walkIn, queueTicket, queueGroup, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, walkIn, queueTicket, queueGroup, started.idempotencyKey());
        IdempotencyRecord completed = completeIdempotency(scope, started, walkIn, queueTicket, queueGroup);

        return WalkInQueueResult.success(
            walkIn.id().value(),
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.partySize().value(),
            queueGroup.groupCode(),
            queueTicket.businessDate().value(),
            queueTicket.queuePosition(),
            completed.status().code(),
            List.of(EVENT_WALK_IN_QUEUED, EVENT_QUEUE_TICKET_CREATED),
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private Customer resolveCustomer(QueueWalkInCommand command, TenantScope tenantScope) {
        if (command.customerId() == null && !hasText(command.customerName()) && !hasText(command.customerNickname()) && !hasText(command.phoneE164())) {
            return null;
        }
        E164Phone phone = parsePhone(command.phoneE164());
        if (command.customerId() != null) {
            Customer customer = customerRepository.findById(tenantScope, new CustomerId(command.customerId()))
                .orElseThrow(() -> new ApplicationFailure(WalkInQueueError.INVALID_CUSTOMER_IDENTITY));
            return refreshCustomerProfile(tenantScope, customer, command, phone);
        }
        if (phone.isPresent()) {
            Optional<Customer> existing = customerRepository.findByPhone(tenantScope, phone);
            if (existing.isPresent()) {
                return refreshCustomerProfile(tenantScope, existing.get(), command, phone);
            }
        }
        try {
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
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private Customer refreshCustomerProfile(TenantScope tenantScope, Customer customer, QueueWalkInCommand command, E164Phone phone) {
        try {
            return customerRepository.save(
                tenantScope,
                customer.refreshProfile(phone, blankToNull(command.customerName()), blankToNull(command.customerNickname()))
            );
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueGroup resolveQueueGroup(StoreScope scope, PartySize partySize) {
        QueueGroup queueGroup = queueGroupRepository.findActiveByPartySize(scope, partySize)
            .orElseThrow(() -> new ApplicationFailure(WalkInQueueError.QUEUE_GROUP_NOT_FOUND));
        RuleDecision decision = queueGroupSelectionRule.validate(queueGroup, partySize);
        if (!decision.accepted()) {
            WalkInQueueError error = WalkInQueueError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == WalkInQueueError.INVALID_COMMAND ? WalkInQueueError.QUEUE_GROUP_PARTY_SIZE_MISMATCH : error);
        }
        return queueGroup;
    }

    private List<QueueTicket> activeQueue(StoreScope scope, QueueGroup queueGroup, BusinessDate businessDate) {
        try {
            return queueTicketRepository.findActiveQueue(scope, queueGroup.id(), businessDate);
        } catch (QueueTicketNumberConflictException exception) {
            throw new ApplicationFailure(WalkInQueueError.QUEUE_TICKET_NUMBER_CONFLICT, exception);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private WalkIn saveWalkIn(StoreScope scope, WalkIn walkIn) {
        try {
            return walkInRepository.save(scope, walkIn);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueTicket saveQueueTicket(StoreScope scope, QueueTicket queueTicket) {
        try {
            return queueTicketRepository.save(scope, queueTicket);
        } catch (QueueTicketNumberConflictException exception) {
            throw new ApplicationFailure(WalkInQueueError.QUEUE_TICKET_NUMBER_CONFLICT, exception);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        QueueWalkInCommand command,
        WalkIn walkIn,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        IdempotencyKey idempotencyKey
    ) {
        String metadata = metadata(walkIn, queueTicket, queueGroup, command, idempotencyKey);
        List<BusinessEvent> events = List.of(
            newBusinessEvent(EVENT_WALK_IN_QUEUED, TARGET_WALK_IN, walkIn.id().value(), command, metadata),
            newBusinessEvent(EVENT_QUEUE_TICKET_CREATED, TARGET_QUEUE_TICKET, queueTicket.id().value(), command, metadata)
        );
        try {
            return events.stream().map(event -> {
                require(
                    businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
                    WalkInQueueError.BUSINESS_EVENT_WRITE_FAILED
                );
                return businessEventRepository.append(scope, event).id();
            }).toList();
        } catch (ApplicationFailure failure) {
            throw failure;
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.BUSINESS_EVENT_WRITE_FAILED, exception);
        }
    }

    private List<UUID> appendTransitionLogs(
        StoreScope scope,
        QueueWalkInCommand command,
        WalkIn walkIn,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        IdempotencyKey idempotencyKey
    ) {
        String metadata = metadata(walkIn, queueTicket, queueGroup, command, idempotencyKey);
        List<StateTransitionLog> logs = List.of(
            newTransition(TARGET_WALK_IN, walkIn.id().value(), "created", "queued", TRANSITION_WALK_IN_QUEUE, command, metadata),
            newTransition(TARGET_QUEUE_TICKET, queueTicket.id().value(), "none", QueueTicketStatus.WAITING.code(), TRANSITION_QUEUE_TICKET_CREATE, command, metadata)
        );
        try {
            return logs.stream().map(log -> {
                require(
                    stateTransitionRule.evaluate(log.targetType(), log.targetId(), log.toStatus(), log.transitionCode(), log.actorType()),
                    WalkInQueueError.STATE_TRANSITION_WRITE_FAILED
                );
                return stateTransitionLogRepository.append(scope, log).id();
            }).toList();
        } catch (ApplicationFailure failure) {
            throw failure;
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.STATE_TRANSITION_WRITE_FAILED, exception);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        QueueWalkInCommand command,
        WalkIn walkIn,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        IdempotencyKey idempotencyKey
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_QUEUE,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(walkIn, queueTicket, queueGroup, command, idempotencyKey)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), WalkInQueueError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(StoreScope scope, QueueWalkInCommand command, IdempotencyKey idempotencyKey, WalkInQueueError error) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_QUEUE_FAILED,
                    TARGET_WALK_IN,
                    null,
                    source(command),
                    command.actorType(),
                    command.actorId(),
                    """
                        {"failureReason":"%s","partySize":%s,"idempotencyKey":"%s"}
                        """.formatted(error.code(), command.partySize() == null ? "null" : command.partySize(), escape(idempotencyKey.value())).trim()
                )
            );
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private BusinessEvent newBusinessEvent(String eventType, String targetType, UUID targetId, QueueWalkInCommand command, String metadata) {
        return new BusinessEvent(UUID.randomUUID(), eventType, targetType, targetId, command.actorType(), command.actorId(), source(command), metadata);
    }

    private StateTransitionLog newTransition(String targetType, UUID targetId, String fromStatus, String toStatus, String transitionCode, QueueWalkInCommand command, String metadata) {
        return new StateTransitionLog(UUID.randomUUID(), targetType, targetId, fromStatus, toStatus, transitionCode, command.actorType(), command.actorId(), source(command), metadata);
    }

    private IdempotencyRecord completeIdempotency(StoreScope scope, IdempotencyRecord started, WalkIn walkIn, QueueTicket queueTicket, QueueGroup queueGroup) {
        IdempotencyRecord completionPayload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            snapshot(walkIn, queueTicket, queueGroup)
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, TARGET_QUEUE_TICKET);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(WalkInQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private WalkInQueueResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            WalkInQueueError error = WalkInQueueError.fromCode(decision.violationCode());
            if (error == WalkInQueueError.IDEMPOTENCY_IN_PROGRESS) {
                return WalkInQueueResult.retryLater(error);
            }
            return WalkInQueueResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return WalkInQueueResult.failure(WalkInQueueError.IDEMPOTENCY_CONFLICT);
            }
        }
        return WalkInQueueResult.failure(WalkInQueueError.IDEMPOTENCY_CONFLICT);
    }

    private WalkInQueueResult replay(String snapshot) {
        return WalkInQueueResult.replay(
            UUID.fromString(extract(snapshot, "walkInId")),
            UUID.fromString(extract(snapshot, "queueTicketId")),
            Integer.parseInt(extractNumber(snapshot, "queueTicketNumber")),
            Integer.parseInt(extractNumber(snapshot, "partySize")),
            extract(snapshot, "partySizeGroup"),
            LocalDate.parse(extract(snapshot, "businessDate")),
            Integer.parseInt(extractNumber(snapshot, "queuePosition"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, WalkInQueueError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static WalkInQueueError validateCommand(QueueWalkInCommand command) {
        if (command == null) {
            return WalkInQueueError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return WalkInQueueError.MISSING_IDEMPOTENCY_KEY;
        }
        if (command.tenantId() == null || command.storeId() == null || command.actorId() == null || !hasText(command.actorType())) {
            return WalkInQueueError.INVALID_COMMAND;
        }
        if (command.partySize() == null || command.partySize() <= 0) {
            return WalkInQueueError.INVALID_PARTY_SIZE;
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
        return new BusinessDate(LocalDate.now(zoneId));
    }

    private static E164Phone parsePhone(String phoneE164) {
        try {
            return hasText(phoneE164) ? new E164Phone(phoneE164.trim()) : E164Phone.empty();
        } catch (IllegalArgumentException exception) {
            throw new ApplicationFailure(WalkInQueueError.INVALID_CUSTOMER_IDENTITY, exception);
        }
    }

    private static void require(RuleDecision decision, WalkInQueueError fallback) {
        if (!decision.accepted()) {
            WalkInQueueError error = WalkInQueueError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == WalkInQueueError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(WalkIn walkIn, QueueTicket queueTicket, QueueGroup queueGroup) {
        return """
            {"walkInId":"%s","queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"waiting","partySize":%d,"partySizeGroup":"%s","businessDate":"%s","queuePosition":%d}
            """.formatted(
            walkIn.id().value(),
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.partySize().value(),
            queueGroup.groupCode(),
            queueTicket.businessDate().value(),
            queueTicket.queuePosition()
        ).trim();
    }

    private static String metadata(WalkIn walkIn, QueueTicket queueTicket, QueueGroup queueGroup, QueueWalkInCommand command, IdempotencyKey idempotencyKey) {
        return """
            {"walkInId":"%s","queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"waiting","queueGroupId":"%s","queueGroupCode":"%s","partySize":%d,"businessDate":"%s","queuePosition":%d,"customerId":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            walkIn.id().value(),
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.queueGroupId(),
            queueGroup.groupCode(),
            queueTicket.partySize().value(),
            queueTicket.businessDate().value(),
            queueTicket.queuePosition(),
            jsonNullable(queueTicket.customerId() == null ? null : queueTicket.customerId().value()),
            jsonNullable(command.note()),
            escape(idempotencyKey.value())
        ).trim();
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

    private static String extractNumber(String json, String key) {
        Pattern number = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = number.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String source(QueueWalkInCommand command) {
        return hasText(command.actorType()) ? command.actorType().trim() : "staff";
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
        private final WalkInQueueError error;
        private final boolean retryLater;

        private ApplicationFailure(WalkInQueueError error) {
            this(error, false, null);
        }

        private ApplicationFailure(WalkInQueueError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(WalkInQueueError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(WalkInQueueError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private WalkInQueueError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
