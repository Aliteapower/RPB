package com.rpb.reservation.reservation.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.reservation.application.ReservationCalendarSummaryResult;
import com.rpb.reservation.reservation.application.ReservationTimeSlotListResult;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import com.rpb.reservation.reservation.application.query.ReservationCalendarSummaryQuery;
import com.rpb.reservation.reservation.application.query.ReservationTimeSlotQuery;
import com.rpb.reservation.reservation.application.query.ReservationTodayViewQuery;
import com.rpb.reservation.reservation.application.service.ReservationTodayViewApplicationService;
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
@RequestMapping("/api/v1/stores/{storeId}/reservations")
public class ReservationTodayViewController {
    private static final String REQUIRED_PERMISSION = "reservation.today_view";
    private static final String CREATE_PERMISSION = "reservation.create";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final ReservationTodayViewApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final ReservationTodayViewApiMapper apiMapper;
    private final ReservationTodayViewApiErrorMapper errorMapper;

    public ReservationTodayViewController(
        ReservationTodayViewApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        ReservationTodayViewApiMapper apiMapper,
        ReservationTodayViewApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/today")
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> todayReservations(
        @PathVariable UUID storeId,
        @RequestParam(value = "businessDate", required = false) String businessDate,
        @RequestParam(value = "status", required = false) String status
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(REQUIRED_PERMISSION)) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        ReservationTodayViewResult result = applicationService.getToday(new ReservationTodayViewQuery(
            actor.tenantId(),
            storeId,
            actor.actorId(),
            actor.actorType(),
            businessDate,
            status
        ));
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    @GetMapping("/calendar-summary")
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> calendarSummary(
        @PathVariable UUID storeId,
        @RequestParam(value = "month", required = false) String month
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(REQUIRED_PERMISSION)) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        ReservationCalendarSummaryResult result = applicationService.getCalendarSummary(new ReservationCalendarSummaryQuery(
            actor.tenantId(),
            storeId,
            actor.actorId(),
            actor.actorType(),
            month
        ));
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    @GetMapping("/time-slots")
    @RequireAppGate(appKey = "reservation_queue", permission = CREATE_PERMISSION)
    public ResponseEntity<?> timeSlots(
        @PathVariable UUID storeId,
        @RequestParam(value = "businessDate", required = false) String businessDate
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(CREATE_PERMISSION)) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        ReservationTimeSlotListResult result = applicationService.getTimeSlots(new ReservationTimeSlotQuery(
            actor.tenantId(),
            storeId,
            actor.actorId(),
            actor.actorType(),
            businessDate
        ));
        if (!result.success()) {
            return errorMapper.toResponse(ReservationTodayViewResult.failure(result.error()));
        }
        return ResponseEntity.ok(ReservationTimeSlotListResponse.from(result));
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
