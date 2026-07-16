package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.QueueDisplayApplicationService;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import com.rpb.reservation.queuedisplay.application.QueueDisplayQuery;
import com.rpb.reservation.queuedisplay.application.QueueDisplayResult;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/queue-display")
public class QueueDisplayController {
    private static final String VIEW_PERMISSION = "queue.display.view";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final QueueDisplayApplicationService service;
    private final CallScreenMediaService mediaService;
    private final CurrentActorProvider currentActorProvider;
    private final QueueDisplayApiErrorMapper errorMapper;

    public QueueDisplayController(
        QueueDisplayApplicationService service,
        CallScreenMediaService mediaService,
        CurrentActorProvider currentActorProvider,
        QueueDisplayApiErrorMapper errorMapper
    ) {
        this.service = service;
        this.mediaService = mediaService;
        this.currentActorProvider = currentActorProvider;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/state")
    @RequireAppGate(appKey = "reservation_queue", permission = VIEW_PERMISSION)
    public ResponseEntity<?> getState(@PathVariable UUID storeId, @RequestParam(required = false) String locale) {
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

        QueueDisplayResult result = service.getState(new QueueDisplayQuery(actor.tenantId(), storeId, actor.actorId(), actor.actorType(), locale));
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(QueueDisplayResponse.from(result));
    }

    @GetMapping("/media/{assetId}")
    @RequireAppGate(appKey = "reservation_queue", permission = VIEW_PERMISSION)
    public ResponseEntity<?> getMedia(@PathVariable UUID storeId, @PathVariable UUID assetId) {
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

        CallScreenMediaContent content = mediaService.readQueueDisplayMedia(
            new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId)),
            assetId
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .header("X-Content-Type-Options", "nosniff")
            .body(content.resource());
    }

    @ExceptionHandler(CallScreenMediaServiceException.class)
    public ResponseEntity<QueueDisplayApiErrorResponse> handleMediaException(CallScreenMediaServiceException exception) {
        return switch (exception.code()) {
            case MEDIA_NOT_FOUND -> errorMapper.toResponse(QueueDisplayApiErrorCode.MEDIA_NOT_FOUND);
            case REQUEST_INVALID -> errorMapper.toResponse(QueueDisplayApiErrorCode.FORBIDDEN);
            case PERSISTENCE_ERROR -> errorMapper.toResponse(QueueDisplayApiErrorCode.PERSISTENCE_ERROR);
        };
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
