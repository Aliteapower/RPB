package com.rpb.reservation.reservation.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.reservation.application.AssignableReservationTablesResult;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentResult;
import com.rpb.reservation.reservation.application.service.ReservationTableAssignmentApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/reservations/{reservationId}")
public class ReservationTableAssignmentController {
    private static final String TABLE_VIEW = "table.view";
    private static final String RESERVATION_CREATE = "reservation.create";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final ReservationTableAssignmentApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final ReservationTableAssignmentApiMapper apiMapper;
    private final ReservationTableAssignmentApiErrorMapper errorMapper;

    @Autowired
    public ReservationTableAssignmentController(
        ReservationTableAssignmentApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        ReservationTableAssignmentApiMapper apiMapper,
        ReservationTableAssignmentApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @GetMapping("/assignable-tables")
    @RequireAppGate(appKey = "reservation_queue", permission = TABLE_VIEW)
    public ResponseEntity<?> listAssignableTables(
        @PathVariable UUID storeId,
        @PathVariable UUID reservationId
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationTableAssignmentApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        ResponseEntity<?> accessError = validateAccess(actor, storeId, TABLE_VIEW);
        if (accessError != null) {
            return accessError;
        }

        AssignableReservationTablesResult result = applicationService.listAssignableTables(
            apiMapper.toQuery(storeId, reservationId, actor)
        );
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    @PutMapping("/table-assignment")
    @RequireAppGate(appKey = "reservation_queue", permission = RESERVATION_CREATE)
    public ResponseEntity<?> assignTable(
        @PathVariable UUID storeId,
        @PathVariable UUID reservationId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody(required = false) AssignReservationTableRequest request
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationTableAssignmentApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        ResponseEntity<?> accessError = validateAccess(actor, storeId, RESERVATION_CREATE);
        if (accessError != null) {
            return accessError;
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return errorMapper.toResponse(ReservationTableAssignmentApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        if (request == null || request.tableId() == null) {
            return errorMapper.toResponse(ReservationTableAssignmentApiErrorCode.INVALID_COMMAND);
        }

        ReservationTableAssignmentResult result = applicationService.assignTable(
            apiMapper.toCommand(request, storeId, reservationId, idempotencyKey, actor)
        );
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private ResponseEntity<?> validateAccess(CurrentActor actor, UUID storeId, String permission) {
        if (!hasAllowedRole(actor) || !actor.hasPermission(permission)) {
            return errorMapper.toResponse(ReservationTableAssignmentApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ReservationTableAssignmentApiErrorCode.STORE_SCOPE_MISMATCH);
        }
        return null;
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
