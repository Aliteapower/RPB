package com.rpb.reservation.table.api;

import com.rpb.reservation.appgate.domain.AppGateRequiredPermission;
import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.table.application.TableSwitchResult;
import com.rpb.reservation.table.application.command.SwitchTableCommand;
import com.rpb.reservation.table.application.service.TableSwitchApplicationService;
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
public class TableSwitchController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String REQUIRED_PERMISSION = AppGateRequiredPermission.TABLE_SWITCH;
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final TableSwitchApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final TableSwitchApiMapper apiMapper;
    private final TableSwitchApiErrorMapper errorMapper;

    public TableSwitchController(
        TableSwitchApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        TableSwitchApiMapper apiMapper,
        TableSwitchApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping("/seatings/{seatingId}/table-switch")
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> switchTable(
        @PathVariable UUID storeId,
        @PathVariable UUID seatingId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody TableSwitchRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(TableSwitchApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(TableSwitchApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        Optional<ResponseEntity<TableSwitchApiErrorResponse>> authError = authorize(actor, storeId);
        if (authError.isPresent()) {
            return authError.get();
        }

        SwitchTableCommand command = apiMapper.toCommand(nonNull(request), storeId, seatingId, idempotencyKey, actor);
        TableSwitchResult result = applicationService.switchTable(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(apiMapper.toResponse(result));
    }

    private Optional<ResponseEntity<TableSwitchApiErrorResponse>> authorize(CurrentActor actor, UUID storeId) {
        if (!hasAllowedRole(actor) || !actor.hasPermission(REQUIRED_PERMISSION)) {
            return Optional.of(errorMapper.toResponse(TableSwitchApiErrorCode.FORBIDDEN));
        }
        if (!actor.canAccessStore(storeId)) {
            return Optional.of(errorMapper.toResponse(TableSwitchApiErrorCode.STORE_SCOPE_MISMATCH));
        }
        return Optional.empty();
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static TableSwitchRequest nonNull(TableSwitchRequest request) {
        return request == null ? new TableSwitchRequest(null, null, null, null) : request;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
