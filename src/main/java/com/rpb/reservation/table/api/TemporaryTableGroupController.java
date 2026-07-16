package com.rpb.reservation.table.api;

import static com.rpb.reservation.appgate.domain.AppGateRequiredPermission.TABLE_SWITCH;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.table.application.TemporaryTableGroupResult;
import com.rpb.reservation.table.application.service.TemporaryTableGroupApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tables/temporary-groups")
public class TemporaryTableGroupController {
    private static final String REQUIRED_PERMISSION = TABLE_SWITCH;
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final TemporaryTableGroupApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final TemporaryTableGroupApiMapper apiMapper;
    private final TemporaryTableGroupApiErrorMapper errorMapper;

    public TemporaryTableGroupController(
        TemporaryTableGroupApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        TemporaryTableGroupApiMapper apiMapper,
        TemporaryTableGroupApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @PostMapping
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> saveTemporaryGroup(
        @PathVariable UUID storeId,
        @RequestBody SaveTemporaryTableGroupRequest request
    ) {
        Optional<CurrentActor> actor = currentActorProvider.currentActor();
        if (actor.isEmpty()) {
            return errorMapper.toResponse(TemporaryTableGroupApiErrorCode.FORBIDDEN);
        }
        Optional<ResponseEntity<TemporaryTableGroupApiErrorResponse>> authError = authorize(actor.get(), storeId);
        if (authError.isPresent()) {
            return authError.get();
        }

        SaveTemporaryTableGroupRequest safeRequest = request == null
            ? new SaveTemporaryTableGroupRequest(null, null, null)
            : request;
        BusinessDate businessDate = parseBusinessDate(safeRequest.businessDate());
        if (safeRequest.businessDate() != null && businessDate == null) {
            return errorMapper.toResponse(TemporaryTableGroupApiErrorCode.INVALID_BUSINESS_DATE);
        }

        TemporaryTableGroupResult result = applicationService.saveForManagement(
            apiMapper.toSaveCommand(safeRequest, storeId, actor.get().tenantId(), businessDate)
        );
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(apiMapper.toResponse(result));
    }

    @DeleteMapping("/{tableGroupId}")
    @RequireAppGate(appKey = "reservation_queue", permission = REQUIRED_PERMISSION)
    public ResponseEntity<?> dissolveTemporaryGroup(
        @PathVariable UUID storeId,
        @PathVariable UUID tableGroupId
    ) {
        Optional<CurrentActor> actor = currentActorProvider.currentActor();
        if (actor.isEmpty()) {
            return errorMapper.toResponse(TemporaryTableGroupApiErrorCode.FORBIDDEN);
        }
        Optional<ResponseEntity<TemporaryTableGroupApiErrorResponse>> authError = authorize(actor.get(), storeId);
        if (authError.isPresent()) {
            return authError.get();
        }

        TemporaryTableGroupResult result = applicationService.dissolveForManagement(
            apiMapper.toDissolveCommand(storeId, actor.get().tenantId(), tableGroupId)
        );
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private Optional<ResponseEntity<TemporaryTableGroupApiErrorResponse>> authorize(CurrentActor actor, UUID storeId) {
        if (!hasAllowedRole(actor) || !actor.hasPermission(REQUIRED_PERMISSION)) {
            return Optional.of(errorMapper.toResponse(TemporaryTableGroupApiErrorCode.FORBIDDEN));
        }
        if (!actor.canAccessStore(storeId)) {
            return Optional.of(errorMapper.toResponse(TemporaryTableGroupApiErrorCode.STORE_SCOPE_MISMATCH));
        }
        return Optional.empty();
    }

    private static BusinessDate parseBusinessDate(String value) {
        if (value == null || value.isBlank()) {
            return new BusinessDate(LocalDate.now());
        }
        try {
            return new BusinessDate(LocalDate.parse(value.trim()));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
