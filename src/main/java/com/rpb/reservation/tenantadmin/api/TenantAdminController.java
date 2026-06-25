package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.application.TenantAdminSearchCommand;
import com.rpb.reservation.tenantadmin.application.TenantAdminServiceException;
import com.rpb.reservation.tenantadmin.application.TenantAdminSettingsCommand;
import com.rpb.reservation.tenantadmin.application.TenantAdminSettingsService;
import com.rpb.reservation.tenantadmin.application.TenantAdminStaffMutationCommand;
import com.rpb.reservation.tenantadmin.application.TenantAdminStaffService;
import com.rpb.reservation.tenantadmin.application.TenantAdminTableMutationCommand;
import com.rpb.reservation.tenantadmin.application.TenantAdminTableService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin")
public class TenantAdminController {
    private static final String TENANT_ADMIN = "tenant_admin";
    private static final String TENANT_ADMIN_MANAGE = "tenant.admin.manage";

    private final TenantAdminStaffService staffService;
    private final TenantAdminTableService tableService;
    private final TenantAdminSettingsService settingsService;
    private final CurrentActorProvider currentActorProvider;

    public TenantAdminController(
        TenantAdminStaffService staffService,
        TenantAdminTableService tableService,
        TenantAdminSettingsService settingsService,
        CurrentActorProvider currentActorProvider
    ) {
        this.staffService = staffService;
        this.tableService = tableService;
        this.settingsService = settingsService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/staff")
    public ResponseEntity<TenantAdminStaffListResponse> listStaff(
        @PathVariable UUID storeId,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", required = false) String limit,
        @RequestParam(value = "offset", required = false) String offset
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminStaffListResponse.from(
            staffService.listStaff(scope, new TenantAdminSearchCommand(keyword, limit, offset))
        ));
    }

    @PostMapping("/staff")
    public ResponseEntity<TenantAdminStaffResponse> createStaff(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminStaffMutationRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.status(201).body(TenantAdminStaffResponse.from(
            staffService.createStaff(scope, toCommand(request))
        ));
    }

    @GetMapping("/staff/{staffId}")
    public ResponseEntity<TenantAdminStaffResponse> getStaff(
        @PathVariable UUID storeId,
        @PathVariable UUID staffId
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminStaffResponse.from(staffService.getStaff(scope, staffId)));
    }

    @PatchMapping("/staff/{staffId}")
    public ResponseEntity<TenantAdminStaffResponse> updateStaff(
        @PathVariable UUID storeId,
        @PathVariable UUID staffId,
        @RequestBody(required = false) TenantAdminStaffMutationRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminStaffResponse.from(
            staffService.updateStaff(scope, staffId, toCommand(request))
        ));
    }

    @GetMapping("/tables")
    public ResponseEntity<TenantAdminTableListResponse> listTables(
        @PathVariable UUID storeId,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", required = false) String limit,
        @RequestParam(value = "offset", required = false) String offset
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminTableListResponse.from(
            tableService.listTables(scope, new TenantAdminSearchCommand(keyword, limit, offset))
        ));
    }

    @PostMapping("/tables")
    public ResponseEntity<TenantAdminTableResponse> createTable(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminTableMutationRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.status(201).body(TenantAdminTableResponse.from(
            tableService.createTable(scope, toCommand(request))
        ));
    }

    @GetMapping("/tables/{tableId}")
    public ResponseEntity<TenantAdminTableResponse> getTable(
        @PathVariable UUID storeId,
        @PathVariable UUID tableId
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminTableResponse.from(tableService.getTable(scope, tableId)));
    }

    @PatchMapping("/tables/{tableId}")
    public ResponseEntity<TenantAdminTableResponse> updateTable(
        @PathVariable UUID storeId,
        @PathVariable UUID tableId,
        @RequestBody(required = false) TenantAdminTableMutationRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminTableResponse.from(
            tableService.updateTable(scope, tableId, toCommand(request))
        ));
    }

    @GetMapping("/settings")
    public ResponseEntity<TenantAdminSettingsResponse> getSettings(@PathVariable UUID storeId) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminSettingsResponse.from(settingsService.getSettings(scope)));
    }

    @PatchMapping("/settings")
    public ResponseEntity<TenantAdminSettingsResponse> updateSettings(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminSettingsRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminSettingsResponse.from(
            settingsService.updateSettings(scope, toCommand(request))
        ));
    }

    @ExceptionHandler(TenantAdminApiException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleApiException(TenantAdminApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler(TenantAdminServiceException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleServiceException(TenantAdminServiceException exception) {
        return apiError(TenantAdminApiErrorCode.valueOf(exception.code().name()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(TenantAdminApiErrorCode.PERSISTENCE_ERROR);
    }

    private StoreScope requireTenantAdminScope(UUID storeId) {
        CurrentActor actor = currentActorProvider.currentActor()
            .orElseThrow(() -> new TenantAdminApiException(TenantAdminApiErrorCode.UNAUTHENTICATED));
        if (!actor.roles().contains(TENANT_ADMIN) || !actor.hasPermission(TENANT_ADMIN_MANAGE)) {
            throw new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN);
        }
        if (actor.tenantId() == null || !actor.storeIds().contains(storeId)) {
            throw new TenantAdminApiException(TenantAdminApiErrorCode.STORE_SCOPE_MISMATCH);
        }
        return new StoreScope(new TenantId(actor.tenantId()), new StoreId(storeId));
    }

    private static ResponseEntity<TenantAdminApiErrorResponse> apiError(TenantAdminApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(TenantAdminApiErrorResponse.of(code));
    }

    private static TenantAdminStaffMutationCommand toCommand(TenantAdminStaffMutationRequest request) {
        if (request == null) {
            return null;
        }
        return new TenantAdminStaffMutationCommand(
            request.employeeNo(),
            request.name(),
            request.phone(),
            request.email(),
            request.status(),
            request.password()
        );
    }

    private static TenantAdminTableMutationCommand toCommand(TenantAdminTableMutationRequest request) {
        if (request == null) {
            return null;
        }
        return new TenantAdminTableMutationCommand(
            request.areaName(),
            request.tableCode(),
            request.capacity(),
            request.enabled()
        );
    }

    private static TenantAdminSettingsCommand toCommand(TenantAdminSettingsRequest request) {
        if (request == null) {
            return null;
        }
        return new TenantAdminSettingsCommand(
            request.storeName(),
            request.timezone(),
            request.locale(),
            request.dateFormat(),
            request.timeFormat(),
            request.currency(),
            request.reservationHoldMinutes(),
            request.queueCallHoldMinutes(),
            request.expectedDiningMinutes()
        );
    }
}
