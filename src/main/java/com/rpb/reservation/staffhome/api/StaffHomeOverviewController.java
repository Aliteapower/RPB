package com.rpb.reservation.staffhome.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewQuery;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewResult;
import com.rpb.reservation.staffhome.application.service.StaffHomeOverviewApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/staff-home")
public class StaffHomeOverviewController {
    private static final String REQUIRED_PERMISSION = "reservation.today_view";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final StaffHomeOverviewApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final StaffHomeOverviewApiMapper apiMapper;
    private final StaffHomeOverviewApiErrorMapper errorMapper;

    public StaffHomeOverviewController(
        StaffHomeOverviewApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        StaffHomeOverviewApiMapper apiMapper,
        StaffHomeOverviewApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/overview")
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> overview(
        @PathVariable UUID storeId,
        @RequestParam(value = "businessDate", required = false) String businessDate
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(StaffHomeOverviewApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(REQUIRED_PERMISSION)) {
            return errorMapper.toResponse(StaffHomeOverviewApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(StaffHomeOverviewApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        StaffHomeOverviewResult result = applicationService.getOverview(new StaffHomeOverviewQuery(
            actor.tenantId(),
            storeId,
            actor.actorId(),
            actor.actorType(),
            businessDate
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
