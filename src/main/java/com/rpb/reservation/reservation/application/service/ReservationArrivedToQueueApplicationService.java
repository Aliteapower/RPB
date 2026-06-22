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
import com.rpb.reservation.reservation.application.ReservationArrivedToQueueError;
import com.rpb.reservation.reservation.application.ReservationArrivedToQueueResult;
import com.rpb.reservation.reservation.application.command.QueueArrivedReservationCommand;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.rule.ReservationArrivedToQueueRule;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.value.ReservationId;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.tenant.value.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean({ QueueGroupRepositoryPort.class, QueueTicketRepositoryPort.class })
public class ReservationArrivedToQueueApplicationService {

    private static final String ACTION = "queue_arrived_reservation";
    private static final String TARGET_RESERVATION = "reservation";
    private static final String TARGET_QUEUE_TICKET = "queue_ticket";
    private static final String EVENT_RESERVATION_QUEUED = "reservation.queued";
    private static final String EVENT_QUEUE_TICKET_CREATED = "queue_ticket.created";
    private static final String OPERATION_QUEUE = "reservation.queue";
    private static final String OPERATION_QUEUE_FAILED = "reservation.queue.failed";
    private static final String TRANSITION_QUEUE_TICKET_CREATE = "queue_ticket.create";

    private final StoreRepositoryPort storeRepository;
    private final ReservationRepositoryPort reservationRepository;
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
    private final ReservationArrivedToQueueRule reservationArrivedToQueueRule = new ReservationArrivedToQueueRule();
    private final QueueGroupSelectionRule queueGroupSelectionRule = new QueueGroupSelectionRule();
    private final QueueTicketNumberPolicy queueTicketNumberPolicy = new QueueTicketNumberPolicy();

    @Autowired
    public ReservationArrivedToQueueApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        QueueGroupRepositoryPort queueGroupRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository
    ) {
        this(
            storeRepository,
            reservationRepository,
            queueGroupRepository,
            queueTicketRepository,
            businessEventRepository,
            stateTransitionLogRepository,
            auditLogRepository,
            idempotencyRepository,
            Clock.systemUTC()
        );
    }

    public ReservationArrivedToQueueApplicationService(
        StoreRepositoryPort storeRepository,
        ReservationRepositoryPort reservationRepository,
        QueueGroupRepositoryPort queueGroupRepository,
        QueueTicketRepositoryPort queueTicketRepository,
        BusinessEventRepositoryPort businessEventRepository,
        StateTransitionLogRepositoryPort stateTransitionLogRepository,
        AuditLogRepositoryPort auditLogRepository,
        IdempotencyRepositoryPort idempotencyRepository,
        Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.reservationRepository = reservationRepository;
        this.queueGroupRepository = queueGroupRepository;
        this.queueTicketRepository = queueTicketRepository;
        this.businessEventRepository = businessEventRepository;
        this.stateTransitionLogRepository = stateTransitionLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationArrivedToQueueResult queueArrivedReservation(QueueArrivedReservationCommand command) {
        ReservationArrivedToQueueError preValidationError = validateCommand(command);
        if (preValidationError != null) {
            return ReservationArrivedToQueueResult.failure(preValidationError);
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
            return ReservationArrivedToQueueResult.failure(ReservationArrivedToQueueError.PERSISTENCE_ERROR);
        }

        try {
            return execute(command, scope, started);
        } catch (ApplicationFailure failure) {
            markFailed(scope, started, failure.error());
            appendFailureAudit(scope, command, started.idempotencyKey(), failure.error());
            return failure.retryLater()
                ? ReservationArrivedToQueueResult.retryLater(failure.error())
                : ReservationArrivedToQueueResult.failure(failure.error());
        } catch (RuntimeException exception) {
            markFailed(scope, started, ReservationArrivedToQueueError.PERSISTENCE_ERROR);
            appendFailureAudit(scope, command, started.idempotencyKey(), ReservationArrivedToQueueError.PERSISTENCE_ERROR);
            return ReservationArrivedToQueueResult.failure(ReservationArrivedToQueueError.PERSISTENCE_ERROR);
        }
    }

    public static String requestHash(QueueArrivedReservationCommand command) {
        String normalized = String.join(
            "|",
            value(command.tenantId()),
            value(command.storeId()),
            value(command.reservationId()),
            value(command.actorId()),
            normalize(command.actorType()),
            normalize(command.partySizeGroup()),
            normalize(command.reasonCode()),
            normalize(command.note())
        );
        return sha256(normalized);
    }

    private ReservationArrivedToQueueResult execute(QueueArrivedReservationCommand command, StoreScope scope, IdempotencyRecord started) {
        Store store = storeRepository.findById(scope)
            .orElseThrow(() -> new ApplicationFailure(ReservationArrivedToQueueError.STORE_NOT_FOUND));
        if (!scope.equals(store.scope())) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.STORE_SCOPE_MISMATCH);
        }
        require(storeAccessPolicy.decide(scope, command.actorId(), command.actorType()), ReservationArrivedToQueueError.STORE_ACCESS_DENIED);

        Reservation reservation = reservationRepository.findById(scope, new ReservationId(command.reservationId()))
            .orElseThrow(() -> new ApplicationFailure(ReservationArrivedToQueueError.RESERVATION_NOT_FOUND));
        if (!scope.equals(reservation.scope())) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.STORE_SCOPE_MISMATCH);
        }

        ReservationArrivedToQueueError statusError = reservationArrivedToQueueRule.validateFreshQueue(reservation.status());
        if (statusError != null) {
            throw new ApplicationFailure(statusError);
        }

        Optional<QueueTicket> activeTicket = queueTicketRepository.findActiveByReservationId(scope, reservation.id().value());
        if (activeTicket.isPresent()) {
            return alreadyQueued(scope, command, started, reservation, activeTicket.get());
        }

        QueueGroup queueGroup = resolveQueueGroup(scope, command, reservation);
        List<QueueTicket> activeQueue = activeQueue(scope, queueGroup, reservation);
        QueueTicketNumber ticketNumber = queueTicketNumberPolicy.nextTicketNumber(activeQueue);
        int queuePosition = queueTicketNumberPolicy.nextQueuePosition(activeQueue);
        QueueTicket queueTicket = createQueueTicket(command, reservation, queueGroup, ticketNumber, queuePosition);
        QueueTicket savedTicket = saveQueueTicket(scope, queueTicket);

        List<UUID> eventIds = appendBusinessEvents(scope, command, reservation, savedTicket, queueGroup, started.idempotencyKey());
        List<UUID> transitionIds = appendTransitionLog(scope, command, reservation, savedTicket, queueGroup, started.idempotencyKey());
        AuditLog auditLog = appendCompletedAudit(scope, command, reservation, savedTicket, queueGroup, started.idempotencyKey());
        IdempotencyRecord completed = completeIdempotency(scope, started, reservation, savedTicket, queueGroup, false);

        return ReservationArrivedToQueueResult.success(
            reservation.id().value(),
            reservation.reservationCode().value(),
            savedTicket.id().value(),
            savedTicket.ticketNumber().value(),
            queueGroup.id(),
            queueGroup.groupCode(),
            savedTicket.partySize().value(),
            queueGroup.groupCode(),
            savedTicket.businessDate().value(),
            savedTicket.queuePosition(),
            completed.status().code(),
            List.of(EVENT_RESERVATION_QUEUED, EVENT_QUEUE_TICKET_CREATED),
            eventIds,
            transitionIds,
            auditLog.id()
        );
    }

    private ReservationArrivedToQueueResult alreadyQueued(
        StoreScope scope,
        QueueArrivedReservationCommand command,
        IdempotencyRecord started,
        Reservation reservation,
        QueueTicket existingTicket
    ) {
        QueueGroup queueGroup = queueGroupRepository.findActiveByCode(scope, queueGroupSelectionRule.defaultGroupCode(existingTicket.partySize()))
            .filter(group -> group.id().equals(existingTicket.queueGroupId()))
            .orElseGet(() -> new QueueGroup(
                existingTicket.queueGroupId(),
                scope,
                queueGroupSelectionRule.defaultGroupCode(existingTicket.partySize()),
                existingTicket.partySize().value(),
                existingTicket.partySize().value(),
                "queue.group.existing",
                "active"
            ));
        IdempotencyRecord completed = completeIdempotency(scope, started, reservation, existingTicket, queueGroup, true);
        return ReservationArrivedToQueueResult.alreadyQueued(
            reservation.id().value(),
            reservation.reservationCode().value(),
            existingTicket.id().value(),
            existingTicket.ticketNumber().value(),
            existingTicket.queueGroupId(),
            queueGroup.groupCode(),
            existingTicket.partySize().value(),
            queueGroup.groupCode(),
            existingTicket.businessDate().value(),
            existingTicket.queuePosition(),
            completed.status().code()
        );
    }

    private QueueGroup resolveQueueGroup(StoreScope scope, QueueArrivedReservationCommand command, Reservation reservation) {
        Optional<QueueGroup> selected;
        if (hasText(command.partySizeGroup())) {
            selected = queueGroupRepository.findActiveByCode(scope, command.partySizeGroup().trim());
        } else {
            selected = queueGroupRepository.findActiveByPartySize(scope, reservation.partySize());
        }
        QueueGroup queueGroup = selected.orElseThrow(() -> new ApplicationFailure(ReservationArrivedToQueueError.QUEUE_GROUP_NOT_FOUND));
        RuleDecision decision = queueGroupSelectionRule.validate(queueGroup, reservation.partySize());
        if (!decision.accepted()) {
            ReservationArrivedToQueueError error = ReservationArrivedToQueueError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == ReservationArrivedToQueueError.INVALID_COMMAND ? ReservationArrivedToQueueError.QUEUE_GROUP_PARTY_SIZE_MISMATCH : error);
        }
        return queueGroup;
    }

    private List<QueueTicket> activeQueue(StoreScope scope, QueueGroup queueGroup, Reservation reservation) {
        try {
            return queueTicketRepository.findActiveQueue(scope, queueGroup.id(), reservation.businessDate());
        } catch (QueueTicketNumberConflictException exception) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.QUEUE_TICKET_NUMBER_CONFLICT, exception);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private QueueTicket createQueueTicket(
        QueueArrivedReservationCommand command,
        Reservation reservation,
        QueueGroup queueGroup,
        QueueTicketNumber ticketNumber,
        int queuePosition
    ) {
        return new QueueTicket(
            new QueueTicketId(UUID.randomUUID()),
            reservation.scope(),
            queueGroup.id(),
            reservation.customerId(),
            reservation.id().value(),
            null,
            ticketNumber,
            reservation.partySize(),
            reservation.businessDate(),
            QueueTicketStatus.WAITING,
            queuePosition,
            blankToNull(command.note())
        );
    }

    private QueueTicket saveQueueTicket(StoreScope scope, QueueTicket queueTicket) {
        try {
            return queueTicketRepository.save(scope, queueTicket);
        } catch (QueueTicketNumberConflictException exception) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.QUEUE_TICKET_NUMBER_CONFLICT, exception);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private List<UUID> appendBusinessEvents(
        StoreScope scope,
        QueueArrivedReservationCommand command,
        Reservation reservation,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        IdempotencyKey idempotencyKey
    ) {
        String metadata = metadata(reservation, queueTicket, queueGroup, command, idempotencyKey, false);
        List<BusinessEvent> events = List.of(
            newBusinessEvent(EVENT_RESERVATION_QUEUED, TARGET_RESERVATION, reservation.id().value(), command, metadata),
            newBusinessEvent(EVENT_QUEUE_TICKET_CREATED, TARGET_QUEUE_TICKET, queueTicket.id().value(), command, metadata)
        );
        List<UUID> ids = new ArrayList<>();
        for (BusinessEvent event : events) {
            require(
                businessEventRule.evaluate(event.eventType(), event.targetType(), event.targetId(), event.actorType()),
                ReservationArrivedToQueueError.BUSINESS_EVENT_WRITE_FAILED
            );
            try {
                ids.add(businessEventRepository.append(scope, event).id());
            } catch (RuntimeException exception) {
                throw new ApplicationFailure(ReservationArrivedToQueueError.BUSINESS_EVENT_WRITE_FAILED, exception);
            }
        }
        return ids;
    }

    private List<UUID> appendTransitionLog(
        StoreScope scope,
        QueueArrivedReservationCommand command,
        Reservation reservation,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        IdempotencyKey idempotencyKey
    ) {
        StateTransitionLog transition = new StateTransitionLog(
            UUID.randomUUID(),
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            "none",
            QueueTicketStatus.WAITING.code(),
            TRANSITION_QUEUE_TICKET_CREATE,
            command.actorType(),
            command.actorId(),
            source(command),
            metadata(reservation, queueTicket, queueGroup, command, idempotencyKey, false)
        );
        require(
            stateTransitionRule.evaluate(
                transition.targetType(),
                transition.targetId(),
                transition.toStatus(),
                transition.transitionCode(),
                transition.actorType()
            ),
            ReservationArrivedToQueueError.STATE_TRANSITION_WRITE_FAILED
        );
        try {
            return List.of(stateTransitionLogRepository.append(scope, transition).id());
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.STATE_TRANSITION_WRITE_FAILED, exception);
        }
    }

    private AuditLog appendCompletedAudit(
        StoreScope scope,
        QueueArrivedReservationCommand command,
        Reservation reservation,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        IdempotencyKey idempotencyKey
    ) {
        AuditLog auditLog = new AuditLog(
            UUID.randomUUID(),
            OPERATION_QUEUE,
            TARGET_RESERVATION,
            reservation.id().value(),
            source(command),
            command.actorType(),
            command.actorId(),
            metadata(reservation, queueTicket, queueGroup, command, idempotencyKey, false)
        );
        require(auditRule.evaluate(auditLog.operationCode(), auditLog.targetType(), auditLog.targetId(), auditLog.actorType()), ReservationArrivedToQueueError.AUDIT_WRITE_FAILED);
        try {
            return auditLogRepository.append(scope, auditLog);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.AUDIT_WRITE_FAILED, exception);
        }
    }

    private void appendFailureAudit(
        StoreScope scope,
        QueueArrivedReservationCommand command,
        IdempotencyKey idempotencyKey,
        ReservationArrivedToQueueError error
    ) {
        try {
            auditLogRepository.append(
                scope,
                new AuditLog(
                    UUID.randomUUID(),
                    OPERATION_QUEUE_FAILED,
                    TARGET_RESERVATION,
                    command.reservationId(),
                    source(command),
                    command.actorType(),
                    command.actorId(),
                    """
                        {"failureReason":"%s","reservationId":"%s","partySizeGroup":%s,"reasonCode":%s,"idempotencyKey":"%s"}
                        """.formatted(
                        error.code(),
                        command.reservationId(),
                        jsonNullable(command.partySizeGroup()),
                        jsonNullable(command.reasonCode()),
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
        QueueArrivedReservationCommand command,
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

    private IdempotencyRecord completeIdempotency(
        StoreScope scope,
        IdempotencyRecord started,
        Reservation reservation,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        boolean alreadyQueued
    ) {
        String snapshot = snapshot(reservation, queueTicket, queueGroup, alreadyQueued);
        IdempotencyRecord completionPayload = new IdempotencyRecord(
            started.id(),
            started.idempotencyKey(),
            started.source(),
            started.action(),
            started.requestHash(),
            IdempotencyStatus.STARTED,
            TARGET_QUEUE_TICKET,
            queueTicket.id().value(),
            snapshot
        );
        try {
            return idempotencyRepository.complete(scope, completionPayload, TARGET_QUEUE_TICKET);
        } catch (RuntimeException exception) {
            throw new ApplicationFailure(ReservationArrivedToQueueError.PERSISTENCE_ERROR, exception);
        }
    }

    private ReservationArrivedToQueueResult resolveExistingIdempotency(IdempotencyRecord existing, String requestHash) {
        RuleDecision decision = idempotencyRule.evaluate(existing, requestHash);
        if (!decision.accepted()) {
            ReservationArrivedToQueueError error = ReservationArrivedToQueueError.fromCode(decision.violationCode());
            if (error == ReservationArrivedToQueueError.COMMAND_IN_PROGRESS) {
                return ReservationArrivedToQueueResult.retryLater(error);
            }
            return ReservationArrivedToQueueResult.failure(error);
        }
        if (existing.status() == IdempotencyStatus.COMPLETED) {
            try {
                return replay(existing.responseSnapshot());
            } catch (RuntimeException exception) {
                return ReservationArrivedToQueueResult.failure(ReservationArrivedToQueueError.IDEMPOTENCY_CONFLICT);
            }
        }
        return ReservationArrivedToQueueResult.failure(ReservationArrivedToQueueError.IDEMPOTENCY_CONFLICT);
    }

    private ReservationArrivedToQueueResult replay(String snapshot) {
        return ReservationArrivedToQueueResult.replay(
            UUID.fromString(extract(snapshot, "reservationId")),
            extract(snapshot, "reservationCode"),
            UUID.fromString(extract(snapshot, "queueTicketId")),
            Integer.parseInt(extractNumber(snapshot, "queueTicketNumber")),
            extract(snapshot, "queueTicketStatus"),
            UUID.fromString(extract(snapshot, "queueGroupId")),
            extract(snapshot, "queueGroupCode"),
            Integer.parseInt(extractNumber(snapshot, "partySize")),
            extract(snapshot, "partySizeGroup"),
            LocalDate.parse(extract(snapshot, "businessDate")),
            Integer.parseInt(extractNumber(snapshot, "queuePosition")),
            Boolean.parseBoolean(extractBoolean(snapshot, "alreadyQueued"))
        );
    }

    private void markFailed(StoreScope scope, IdempotencyRecord started, ReservationArrivedToQueueError error) {
        try {
            idempotencyRepository.fail(scope, started, error.code());
        } catch (RuntimeException ignored) {
            // Preserve the original application failure.
        }
    }

    private static ReservationArrivedToQueueError validateCommand(QueueArrivedReservationCommand command) {
        if (command == null) {
            return ReservationArrivedToQueueError.INVALID_COMMAND;
        }
        if (!hasText(command.idempotencyKey())) {
            return ReservationArrivedToQueueError.MISSING_IDEMPOTENCY_KEY;
        }
        if (
            command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
        ) {
            return ReservationArrivedToQueueError.INVALID_COMMAND;
        }
        return null;
    }

    private static void require(RuleDecision decision, ReservationArrivedToQueueError fallback) {
        if (!decision.accepted()) {
            ReservationArrivedToQueueError error = ReservationArrivedToQueueError.fromCode(decision.violationCode());
            throw new ApplicationFailure(error == ReservationArrivedToQueueError.INVALID_COMMAND ? fallback : error);
        }
    }

    private static String snapshot(Reservation reservation, QueueTicket queueTicket, QueueGroup queueGroup, boolean alreadyQueued) {
        return """
            {"reservationId":"%s","reservationCode":"%s","reservationStatus":"arrived","queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"%s","queueGroupId":"%s","queueGroupCode":"%s","partySize":%d,"partySizeGroup":"%s","businessDate":"%s","queuePosition":%d,"alreadyQueued":%s}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.status().code(),
            queueTicket.queueGroupId(),
            queueGroup.groupCode(),
            queueTicket.partySize().value(),
            queueGroup.groupCode(),
            queueTicket.businessDate().value(),
            queueTicket.queuePosition(),
            alreadyQueued
        ).trim();
    }

    private static String metadata(
        Reservation reservation,
        QueueTicket queueTicket,
        QueueGroup queueGroup,
        QueueArrivedReservationCommand command,
        IdempotencyKey idempotencyKey,
        boolean alreadyQueued
    ) {
        return """
            {"reservationId":"%s","reservationCode":"%s","beforeReservationStatus":"arrived","afterReservationStatus":"arrived","queueTicketId":"%s","queueTicketNumber":%d,"queueTicketStatus":"%s","queueGroupId":"%s","queueGroupCode":"%s","partySize":%d,"businessDate":"%s","queuePosition":%d,"alreadyQueued":%s,"partySizeGroup":%s,"reasonCode":%s,"note":%s,"idempotencyKey":"%s"}
            """.formatted(
            reservation.id().value(),
            reservation.reservationCode().value(),
            queueTicket.id().value(),
            queueTicket.ticketNumber().value(),
            queueTicket.status().code(),
            queueTicket.queueGroupId(),
            queueGroup.groupCode(),
            queueTicket.partySize().value(),
            queueTicket.businessDate().value(),
            queueTicket.queuePosition(),
            alreadyQueued,
            jsonNullable(command.partySizeGroup()),
            jsonNullable(command.reasonCode()),
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

    private static String extractBoolean(String json, String key) {
        Pattern bool = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = bool.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("snapshot_field_missing_" + key);
    }

    private static String source(QueueArrivedReservationCommand command) {
        return hasText(command.actorType()) ? command.actorType().trim() : "staff";
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
        private final ReservationArrivedToQueueError error;
        private final boolean retryLater;

        private ApplicationFailure(ReservationArrivedToQueueError error) {
            this(error, false, null);
        }

        private ApplicationFailure(ReservationArrivedToQueueError error, Throwable cause) {
            this(error, false, cause);
        }

        private ApplicationFailure(ReservationArrivedToQueueError error, boolean retryLater) {
            this(error, retryLater, null);
        }

        private ApplicationFailure(ReservationArrivedToQueueError error, boolean retryLater, Throwable cause) {
            super(error.code(), cause);
            this.error = error;
            this.retryLater = retryLater;
        }

        private ReservationArrivedToQueueError error() {
            return error;
        }

        private boolean retryLater() {
            return retryLater;
        }
    }
}
