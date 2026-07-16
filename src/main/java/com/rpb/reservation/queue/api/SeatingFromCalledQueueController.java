package com.rpb.reservation.queue.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queue.application.SeatingFromCalledQueueResult;
import com.rpb.reservation.queue.application.command.SeatCalledQueueTicketCommand;
import com.rpb.reservation.queue.application.service.SeatingFromCalledQueueApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
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
@RequestMapping("/api/v1/stores/{storeId}/queue-tickets")
public class SeatingFromCalledQueueController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String SEAT_PERMISSION = "queue.seat";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final SeatingFromCalledQueueApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final SeatingFromCalledQueueApiMapper apiMapper;
    private final SeatingFromCalledQueueApiErrorMapper errorMapper;

    @Autowired
    public SeatingFromCalledQueueController(
        SeatingFromCalledQueueApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        SeatingFromCalledQueueApiMapper apiMapper,
        SeatingFromCalledQueueApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/{queueTicketId}/seating/direct")
    @RequireAppGate(appKey = "reservation_queue", permission = SEAT_PERMISSION)
    public ResponseEntity<?> seatCalledQueueTicket(
        @PathVariable UUID storeId,
        @PathVariable UUID queueTicketId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) SeatCalledQueueTicketRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(SeatingFromCalledQueueApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        SeatCalledQueueTicketRequest nonNullRequest = nonNull(request);
        Optional<SeatingFromCalledQueueApiErrorCode> validationError = nonNullRequest.validateContract();
        if (validationError.isPresent()) {
            return errorMapper.toResponse(validationError.get());
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(SeatingFromCalledQueueApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(SEAT_PERMISSION)) {
            return errorMapper.toResponse(SeatingFromCalledQueueApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(SeatingFromCalledQueueApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        SeatCalledQueueTicketCommand command = apiMapper.toCommand(
            nonNullRequest,
            storeId,
            queueTicketId,
            idempotencyKey,
            actor
        );
        SeatingFromCalledQueueResult result = applicationService.seatCalledQueueTicket(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private static SeatCalledQueueTicketRequest nonNull(SeatCalledQueueTicketRequest request) {
        return request == null ? new SeatCalledQueueTicketRequest(null, null, List.of(), null, null, null) : request;
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
