package com.rpb.reservation.reservation.api;

import com.rpb.reservation.appgate.domain.AppGateRequiredPermission;
import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.reservation.application.ReservationShareInfoResult;
import com.rpb.reservation.reservation.application.query.ReservationShareInfoQuery;
import com.rpb.reservation.reservation.application.service.ReservationShareInfoApplicationService;
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
@RequestMapping("/api/v1/stores/{storeId}/reservations")
public class ReservationShareInfoController {
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final ReservationShareInfoApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final ReservationShareInfoApiMapper apiMapper;
    private final ReservationTodayViewApiErrorMapper errorMapper;

    public ReservationShareInfoController(
        ReservationShareInfoApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        ReservationShareInfoApiMapper apiMapper,
        ReservationTodayViewApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/{reservationId}/share-info")
    @RequireAppGate(appKey = "reservation_queue", permission = AppGateRequiredPermission.RESERVATION_TODAY_VIEW)
    public ResponseEntity<?> shareInfo(@PathVariable UUID storeId, @PathVariable UUID reservationId) {
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
            actor.actorType()
        ));
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
