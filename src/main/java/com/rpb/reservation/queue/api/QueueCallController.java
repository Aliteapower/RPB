package com.rpb.reservation.queue.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queue.application.QueueCallResult;
import com.rpb.reservation.queue.application.command.CallQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueCallApplicationService;
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
public class QueueCallController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CALL_PERMISSION = "queue.call";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final QueueCallApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final QueueCallApiMapper apiMapper;
    private final QueueCallApiErrorMapper errorMapper;

    @Autowired
    public QueueCallController(
        QueueCallApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        QueueCallApiMapper apiMapper,
        QueueCallApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/{queueTicketId}/call")
    @RequireAppGate(appKey = "reservation_queue", permission = CALL_PERMISSION)
    public ResponseEntity<?> callQueueTicket(
        @PathVariable UUID storeId,
        @PathVariable UUID queueTicketId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) CallQueueTicketRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(QueueCallApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(QueueCallApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(CALL_PERMISSION)) {
            return errorMapper.toResponse(QueueCallApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(QueueCallApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        CallQueueTicketCommand command = apiMapper.toCommand(nonNull(request), storeId, queueTicketId, idempotencyKey, actor);
        QueueCallResult result = applicationService.callQueueTicket(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private static CallQueueTicketRequest nonNull(CallQueueTicketRequest request) {
        return request == null ? new CallQueueTicketRequest(null, null, null) : request;
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
