package com.rpb.reservation.queue.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queue.application.QueueSkipResult;
import com.rpb.reservation.queue.application.command.SkipQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueSkipApplicationService;
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
public class QueueSkipController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String SKIP_PERMISSION = "queue.skip";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final QueueSkipApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final QueueSkipApiMapper apiMapper;
    private final QueueSkipApiErrorMapper errorMapper;

    @Autowired
    public QueueSkipController(
        QueueSkipApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        QueueSkipApiMapper apiMapper,
        QueueSkipApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/{queueTicketId}/skip")
    @RequireAppGate(appKey = "reservation_queue", permission = SKIP_PERMISSION)
    public ResponseEntity<?> skipQueueTicket(
        @PathVariable UUID storeId,
        @PathVariable UUID queueTicketId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) SkipQueueTicketRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(QueueSkipApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(QueueSkipApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(SKIP_PERMISSION)) {
            return errorMapper.toResponse(QueueSkipApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(QueueSkipApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        SkipQueueTicketCommand command = apiMapper.toCommand(nonNull(request), storeId, queueTicketId, idempotencyKey, actor);
        QueueSkipResult result = applicationService.skipQueueTicket(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private static SkipQueueTicketRequest nonNull(SkipQueueTicketRequest request) {
        return request == null ? new SkipQueueTicketRequest(null, null, null) : request;
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
