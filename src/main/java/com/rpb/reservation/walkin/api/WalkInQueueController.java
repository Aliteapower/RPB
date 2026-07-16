package com.rpb.reservation.walkin.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.walkin.application.WalkInQueueResult;
import com.rpb.reservation.walkin.application.command.QueueWalkInCommand;
import com.rpb.reservation.walkin.application.service.WalkInQueueApplicationService;
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
@RequestMapping("/api/v1/stores/{storeId}/walk-ins")
public class WalkInQueueController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String REQUIRED_PERMISSION = "walkin.queue.create";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final WalkInQueueApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final WalkInQueueApiMapper apiMapper;
    private final WalkInQueueApiErrorMapper errorMapper;

    @Autowired
    public WalkInQueueController(
        WalkInQueueApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        WalkInQueueApiMapper apiMapper,
        WalkInQueueApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/queue")
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> queueWalkIn(
        @PathVariable UUID storeId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) QueueWalkInRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(WalkInQueueApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(WalkInQueueApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(REQUIRED_PERMISSION)) {
            return errorMapper.toResponse(WalkInQueueApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(WalkInQueueApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        QueueWalkInRequest nonNullRequest = request == null ? new QueueWalkInRequest(null, null, null, null, null, null) : request;
        if (nonNullRequest.partySize() == null || nonNullRequest.partySize() <= 0) {
            return errorMapper.toResponse(WalkInQueueApiErrorCode.INVALID_PARTY_SIZE);
        }

        QueueWalkInCommand command = apiMapper.toCommand(nonNullRequest, storeId, idempotencyKey, actor);
        WalkInQueueResult result = applicationService.queueWalkIn(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.status(result.replayed() ? 200 : 201).body(apiMapper.toResponse(result));
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
