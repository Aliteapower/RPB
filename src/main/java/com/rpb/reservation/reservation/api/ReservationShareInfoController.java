package com.rpb.reservation.reservation.api;

import com.rpb.reservation.appgate.domain.AppGateRequiredPermission;
import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.reservation.application.ReservationShareInfoResult;
import com.rpb.reservation.reservation.application.ReservationShareIntentResult;
import com.rpb.reservation.reservation.application.command.ReservationShareIntentCommand;
import com.rpb.reservation.reservation.application.query.ReservationShareInfoQuery;
import com.rpb.reservation.reservation.application.service.ReservationShareInfoApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationShareIntentApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/reservations")
public class ReservationShareInfoController {
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final ReservationShareInfoApplicationService applicationService;
    private final ReservationShareIntentApplicationService shareIntentService;
    private final CurrentActorProvider currentActorProvider;
    private final ReservationShareInfoApiMapper apiMapper;
    private final ReservationTodayViewApiErrorMapper errorMapper;

    public ReservationShareInfoController(
        ReservationShareInfoApplicationService applicationService,
        ReservationShareIntentApplicationService shareIntentService,
        CurrentActorProvider currentActorProvider,
        ReservationShareInfoApiMapper apiMapper,
        ReservationTodayViewApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.shareIntentService = shareIntentService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/{reservationId}/share-info")
    @RequireAppGate(appKey = "reservation_queue", permission = AppGateRequiredPermission.RESERVATION_TODAY_VIEW)
    public ResponseEntity<?> shareInfo(
        @PathVariable UUID storeId,
        @PathVariable UUID reservationId,
        @RequestParam(required = false) String locale,
        HttpServletRequest request
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(AppGateRequiredPermission.RESERVATION_TODAY_VIEW)) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        ReservationShareInfoResult result = applicationService.getShareInfo(new ReservationShareInfoQuery(
            actor.tenantId(),
            storeId,
            reservationId,
            actor.actorId(),
            actor.actorType(),
            requestOrigin(request),
            locale
        ));
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    @PostMapping("/{reservationId}/share-info/intent")
    @RequireAppGate(appKey = "reservation_queue", permission = AppGateRequiredPermission.RESERVATION_TODAY_VIEW)
    public ResponseEntity<?> recordShareIntent(
        @PathVariable UUID storeId,
        @PathVariable UUID reservationId,
        @RequestParam(required = false) String locale,
        @RequestBody(required = false) ReservationShareIntentRequest body,
        HttpServletRequest request
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(AppGateRequiredPermission.RESERVATION_TODAY_VIEW)) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        ReservationShareIntentResult result = shareIntentService.recordIntent(new ReservationShareIntentCommand(
            actor.tenantId(),
            storeId,
            reservationId,
            actor.actorId(),
            actor.actorType(),
            requestOrigin(request),
            body == null ? null : body.channel(),
            locale
        ));
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(ReservationShareIntentResponse.success(result.channel()));
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static String requestOrigin(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromRequestUri(request)
            .replacePath(null)
            .replaceQuery(null)
            .fragment(null)
            .build()
            .toUriString();
    }
}
