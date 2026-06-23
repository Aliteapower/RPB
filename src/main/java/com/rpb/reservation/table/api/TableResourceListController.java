package com.rpb.reservation.table.api;

import static com.rpb.reservation.appgate.domain.AppGateRequiredPermission.TABLE_VIEW;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.table.application.TableResourceItem;
import com.rpb.reservation.table.application.TableResourceListQuery;
import com.rpb.reservation.table.application.TableResourceListResult;
import com.rpb.reservation.table.application.service.TableResourceListApplicationService;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tables")
public class TableResourceListController {
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");
    private static final Set<String> ALLOWED_STATUSES = Set.of(
        "available",
        "locked",
        "reserved",
        "occupied",
        "cleaning",
        "inactive",
        "created",
        "active",
        "deleted",
        "released",
        "ended"
    );

    private final TableResourceListApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final TableResourceListApiErrorMapper errorMapper;

    @Autowired
    public TableResourceListController(
        TableResourceListApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        TableResourceListApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.errorMapper = errorMapper;
    }

    @GetMapping
    @RequireAppGate(appKey = "reservation_queue", permission = TABLE_VIEW)
    public ResponseEntity<?> listTableResources(
        @PathVariable UUID storeId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "partySize", required = false) String partySize,
        @RequestParam(value = "includeGroups", required = false) String includeGroups
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(TableResourceListApiErrorCode.FORBIDDEN);
        }

        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(TABLE_VIEW)) {
            return errorMapper.toResponse(TableResourceListApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(TableResourceListApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        String normalizedStatus = normalize(status);
        if (normalizedStatus != null && !ALLOWED_STATUSES.contains(normalizedStatus)) {
            return errorMapper.toResponse(TableResourceListApiErrorCode.INVALID_STATUS);
        }

        Integer parsedPartySize = parsePartySize(partySize);
        if (partySize != null && parsedPartySize == null) {
            return errorMapper.toResponse(TableResourceListApiErrorCode.INVALID_PARTY_SIZE);
        }

        TableResourceListQuery query = new TableResourceListQuery(
            new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId)),
            normalizedStatus,
            parsedPartySize,
            !"false".equalsIgnoreCase(normalize(includeGroups))
        );
        TableResourceListResult result = applicationService.listResources(query);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.ok(toResponse(result));
    }

    private static TableResourceListResponse toResponse(TableResourceListResult result) {
        return new TableResourceListResponse(
            true,
            result.resources().stream().map(TableResourceListController::toResponse).toList()
        );
    }

    private static TableResourceItemResponse toResponse(TableResourceItem item) {
        return new TableResourceItemResponse(
            item.resourceType(),
            item.resourceId(),
            item.code(),
            item.displayName(),
            item.areaName(),
            item.capacityMin(),
            item.capacityMax(),
            item.status(),
            item.selectable(),
            item.selectionDisabledReason(),
            item.memberTableCodes()
        );
    }

    private static Integer parsePartySize(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(normalized);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase();
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
