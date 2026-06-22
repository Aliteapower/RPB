package com.rpb.reservation.cleaning.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.cleaning.application.CleaningApplicationResult;
import com.rpb.reservation.cleaning.application.command.CompleteCleaningCommand;
import com.rpb.reservation.cleaning.application.command.StartCleaningCommand;
import com.rpb.reservation.cleaning.application.service.CleaningApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
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
@RequestMapping("/api/v1/stores/{storeId}")
public class CleaningController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String START_PERMISSION = "cleaning.start";
    private static final String COMPLETE_PERMISSION = "cleaning.complete";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final CleaningApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final CleaningApiMapper apiMapper;
    private final CleaningApiErrorMapper errorMapper;

    public CleaningController(
        CleaningApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        CleaningApiMapper apiMapper,
        CleaningApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/seatings/{seatingId}/cleaning/start")
    @RequireAppGate(appKey = "reservation_queue", permission = START_PERMISSION)
    public ResponseEntity<?> startCleaning(
        @PathVariable UUID storeId,
        @PathVariable UUID seatingId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody StartCleaningRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(CleaningApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(CleaningApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        Optional<ResponseEntity<CleaningApiErrorResponse>> authError = authorize(actor, storeId, START_PERMISSION);
        if (authError.isPresent()) {
            return authError.get();
        }

        StartCleaningCommand command = apiMapper.toCommand(nonNull(request), storeId, seatingId, idempotencyKey, actor);
        CleaningApplicationResult result = applicationService.startCleaning(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(apiMapper.toStartResponse(result));
    }

    @PostMapping("/cleanings/{cleaningId}/complete")
    @RequireAppGate(appKey = "reservation_queue", permission = COMPLETE_PERMISSION)
    public ResponseEntity<?> completeCleaning(
        @PathVariable UUID storeId,
        @PathVariable UUID cleaningId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody CompleteCleaningRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(CleaningApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(CleaningApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        Optional<ResponseEntity<CleaningApiErrorResponse>> authError = authorize(actor, storeId, COMPLETE_PERMISSION);
        if (authError.isPresent()) {
            return authError.get();
        }

        CompleteCleaningCommand command = apiMapper.toCommand(nonNull(request), storeId, cleaningId, idempotencyKey, actor);
        CleaningApplicationResult result = applicationService.completeCleaning(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toCompleteResponse(result));
    }

    private Optional<ResponseEntity<CleaningApiErrorResponse>> authorize(CurrentActor actor, UUID storeId, String permission) {
        if (!hasAllowedRole(actor) || !actor.hasPermission(permission)) {
            return Optional.of(errorMapper.toResponse(CleaningApiErrorCode.FORBIDDEN));
        }
        if (!actor.canAccessStore(storeId)) {
            return Optional.of(errorMapper.toResponse(CleaningApiErrorCode.STORE_SCOPE_MISMATCH));
        }
        return Optional.empty();
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static StartCleaningRequest nonNull(StartCleaningRequest request) {
        return request == null ? new StartCleaningRequest(null, null) : request;
    }

    private static CompleteCleaningRequest nonNull(CompleteCleaningRequest request) {
        return request == null ? new CompleteCleaningRequest(null, null) : request;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
