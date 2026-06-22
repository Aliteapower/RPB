package com.rpb.reservation.walkin.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.walkin.application.WalkInDirectSeatingResult;
import com.rpb.reservation.walkin.application.command.SeatWalkInDirectlyCommand;
import com.rpb.reservation.walkin.application.service.WalkInDirectSeatingApplicationService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/walk-ins")
public class WalkInDirectSeatingController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String REQUIRED_PERMISSION = "walkin.direct_seating.create";

    private final WalkInDirectSeatingApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final WalkInDirectSeatingApiMapper apiMapper;
    private final WalkInDirectSeatingApiErrorMapper errorMapper;

    public WalkInDirectSeatingController(
        WalkInDirectSeatingApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        WalkInDirectSeatingApiMapper apiMapper,
        WalkInDirectSeatingApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/direct-seating")
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> seatWalkInDirectly(
        @PathVariable UUID storeId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody SeatWalkInDirectlyRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(ApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        Optional<ApiErrorCode> validationError = request.validateContract();
        if (validationError.isPresent()) {
            return errorMapper.toResponse(validationError.get());
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!actor.hasAllowedWalkInDirectSeatingRole() || !actor.hasPermission(REQUIRED_PERMISSION)) {
            return errorMapper.toResponse(ApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        SeatWalkInDirectlyCommand command = apiMapper.toCommand(request, storeId, idempotencyKey, actor);
        WalkInDirectSeatingResult result = applicationService.seatWalkInDirectly(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(apiMapper.toResponse(result));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
