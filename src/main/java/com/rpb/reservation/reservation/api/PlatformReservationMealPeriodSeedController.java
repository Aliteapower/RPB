package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.service.ReservationMealPeriodManagementService;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodServiceErrorCode;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodServiceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/reservation/meal-period-seed")
public class PlatformReservationMealPeriodSeedController {
    private static final String PLATFORM_ADMIN = "platform_admin";
    private static final String PLATFORM_RESERVATION_MEAL_PERIOD_MANAGE = "platform.reservation_meal_period.manage";

    private final ReservationMealPeriodManagementService service;
    private final CurrentActorProvider currentActorProvider;

    public PlatformReservationMealPeriodSeedController(
        ReservationMealPeriodManagementService service,
        CurrentActorProvider currentActorProvider
    ) {
        this.service = service;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping
    public ResponseEntity<PlatformReservationMealPeriodSeedResponse> listSeedPeriods() {
        requirePlatformMealPeriodManager();
        return ResponseEntity.ok(PlatformReservationMealPeriodSeedResponse.from(service.getPlatformSeedPeriods()));
    }

    @PatchMapping
    public ResponseEntity<PlatformReservationMealPeriodSeedResponse> replaceSeedPeriods(
        @RequestBody(required = false) PlatformReservationMealPeriodSeedRequest request
    ) {
        requirePlatformMealPeriodManager();
        if (request == null) {
            throw new ReservationMealPeriodApiException(ReservationMealPeriodApiErrorCode.REQUEST_INVALID);
        }
        return ResponseEntity.ok(PlatformReservationMealPeriodSeedResponse.from(
            service.replacePlatformSeedPeriods(request.commands())
        ));
    }

    @ExceptionHandler(ReservationMealPeriodApiException.class)
    public ResponseEntity<ReservationMealPeriodApiErrorResponse> handleApiException(
        ReservationMealPeriodApiException exception
    ) {
        return apiError(exception.code());
    }

    @ExceptionHandler(ReservationMealPeriodServiceException.class)
    public ResponseEntity<ReservationMealPeriodApiErrorResponse> handleServiceException(
        ReservationMealPeriodServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ReservationMealPeriodApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(ReservationMealPeriodApiErrorCode.PERSISTENCE_ERROR);
    }

    private CurrentActor requirePlatformMealPeriodManager() {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new ReservationMealPeriodApiException(ReservationMealPeriodApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(PLATFORM_ADMIN) || !actor.hasPermission(PLATFORM_RESERVATION_MEAL_PERIOD_MANAGE)) {
            throw new ReservationMealPeriodApiException(ReservationMealPeriodApiErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private static ResponseEntity<ReservationMealPeriodApiErrorResponse> apiError(ReservationMealPeriodApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(ReservationMealPeriodApiErrorResponse.of(code));
    }

    private static ReservationMealPeriodApiErrorCode toApiError(ReservationMealPeriodServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> ReservationMealPeriodApiErrorCode.REQUEST_INVALID;
            case PERSISTENCE_ERROR -> ReservationMealPeriodApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
