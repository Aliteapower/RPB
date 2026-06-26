package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queuedisplay.application.QueueDisplayApplicationService;
import com.rpb.reservation.queuedisplay.application.QueueDisplayQuery;
import com.rpb.reservation.queuedisplay.application.QueueDisplayResult;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/queue-display")
public class QueueDisplayController {
    private static final String VIEW_PERMISSION = "queue.display.view";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final QueueDisplayApplicationService service;
    private final CurrentActorProvider currentActorProvider;
    private final QueueDisplayApiErrorMapper errorMapper;

    public QueueDisplayController(
        QueueDisplayApplicationService service,
        CurrentActorProvider currentActorProvider,
        QueueDisplayApiErrorMapper errorMapper
    ) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/state")
    @RequireAppGate(appKey = "reservation_queue", permission = VIEW_PERMISSION)
    public ResponseEntity<?> getState(@PathVariable UUID storeId) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(QueueDisplayApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(VIEW_PERMISSION)) {
            return errorMapper.toResponse(QueueDisplayApiErrorCode.FORBIDDEN);
        }
        if (!actor.storeIds().contains(storeId)) {
            return errorMapper.toResponse(QueueDisplayApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        QueueDisplayResult result = service.getState(new QueueDisplayQuery(actor.tenantId(), storeId, actor.actorId(), actor.actorType()));
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(QueueDisplayResponse.from(result));
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
