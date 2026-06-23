package com.rpb.reservation.queue.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queue.application.QueueRejoinResult;
import com.rpb.reservation.queue.application.command.RejoinQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueRejoinApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/queue-tickets")
public class QueueRejoinController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String REJOIN_PERMISSION = "queue.rejoin";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final QueueRejoinApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final QueueRejoinApiMapper apiMapper;
    private final QueueRejoinApiErrorMapper errorMapper;

    @Autowired
    public QueueRejoinController(
        QueueRejoinApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        QueueRejoinApiMapper apiMapper,
        QueueRejoinApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/{queueTicketId}/rejoin")
    @RequireAppGate(appKey = "reservation_queue", permission = REJOIN_PERMISSION)
    public ResponseEntity<?> rejoinQueueTicket(
        @PathVariable UUID storeId,
        @PathVariable UUID queueTicketId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) RejoinQueueTicketRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(QueueRejoinApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(QueueRejoinApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(REJOIN_PERMISSION)) {
            return errorMapper.toResponse(QueueRejoinApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(QueueRejoinApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        RejoinQueueTicketCommand command = apiMapper.toCommand(nonNull(request), storeId, queueTicketId, idempotencyKey, actor);
        QueueRejoinResult result = applicationService.rejoinQueueTicket(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private static RejoinQueueTicketRequest nonNull(RejoinQueueTicketRequest request) {
        return request == null ? new RejoinQueueTicketRequest(null) : request;
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
