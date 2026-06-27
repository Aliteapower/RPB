package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfileCommand;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfileService;
import com.rpb.reservation.tenantadmin.application.TenantAdminSearchCommand;
import com.rpb.reservation.tenantadmin.application.TenantAdminServiceErrorCode;
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
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin")
public class TenantAdminController {
    private static final String TENANT_ADMIN = "tenant_admin";
    private static final String TENANT_ADMIN_MANAGE = "tenant.admin.manage";
    private static final String EXCEL_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TenantAdminStaffService staffService;
    private final TenantAdminTableService tableService;
    private final TenantAdminSettingsService settingsService;
    private final TenantAdminProfileService profileService;
    private final CurrentActorProvider currentActorProvider;

    public TenantAdminController(
        TenantAdminStaffService staffService,
        TenantAdminTableService tableService,
        TenantAdminSettingsService settingsService,
        TenantAdminProfileService profileService,
        CurrentActorProvider currentActorProvider
    ) {
        this.staffService = staffService;
        this.tableService = tableService;
        this.settingsService = settingsService;
        this.profileService = profileService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/profile")
    public ResponseEntity<TenantAdminProfileResponse> getProfile(@PathVariable UUID storeId) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminProfileResponse.from(scope, profileService.getProfile(scope)));
    }

    @PatchMapping("/profile")
    public ResponseEntity<TenantAdminProfileResponse> updateProfile(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminProfileRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminProfileResponse.from(scope, profileService.updateProfile(scope, toCommand(request))));
    }

    @PostMapping(value = "/profile/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TenantAdminProfileResponse> uploadProfileLogo(
        @PathVariable UUID storeId,
        @RequestParam("file") MultipartFile file
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminProfileResponse.from(scope, profileService.uploadLogo(scope, file)));
    }

    @DeleteMapping("/profile/logo")
    public ResponseEntity<TenantAdminProfileResponse> clearProfileLogo(@PathVariable UUID storeId) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminProfileResponse.from(scope, profileService.clearLogo(scope)));
    }

    @GetMapping("/profile/logo/media/{assetId}")
    public ResponseEntity<Resource> readProfileLogo(
        @PathVariable UUID storeId,
        @PathVariable UUID assetId
    ) {
        CallScreenMediaContent content = profileService.readLogoMedia(requireTenantAdminScope(storeId), assetId);
        return mediaResponse(content);
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

    @GetMapping(value = "/tables/export", produces = EXCEL_MEDIA_TYPE)
    public ResponseEntity<byte[]> exportTables(@PathVariable UUID storeId) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tenant-admin-tables.xlsx\"")
            .contentType(MediaType.parseMediaType(EXCEL_MEDIA_TYPE))
            .body(tableService.exportTables(scope));
    }

    @PostMapping(value = "/tables/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TenantAdminTableImportResponse> importTables(
        @PathVariable UUID storeId,
        @RequestPart("file") MultipartFile file
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        try {
            return ResponseEntity.ok(TenantAdminTableImportResponse.from(tableService.importTables(scope, file.getBytes())));
        } catch (java.io.IOException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
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

    @ExceptionHandler(CallScreenMediaServiceException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleMediaServiceException(
        CallScreenMediaServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
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

    private static ResponseEntity<Resource> mediaResponse(CallScreenMediaContent content) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .header("X-Content-Type-Options", "nosniff")
            .body(content.resource());
    }

    private static TenantAdminApiErrorCode toApiError(CallScreenMediaServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> TenantAdminApiErrorCode.REQUEST_INVALID;
            case MEDIA_NOT_FOUND -> TenantAdminApiErrorCode.MEDIA_NOT_FOUND;
            case PERSISTENCE_ERROR -> TenantAdminApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static TenantAdminProfileCommand toCommand(TenantAdminProfileRequest request) {
        if (request == null) {
            return null;
        }
        return new TenantAdminProfileCommand(
            request.displayName(),
            request.defaultLocale(),
            request.contactPhone(),
            request.address(),
            request.principalName()
        );
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
            request.enabled(),
            request.areaSortOrder(),
            request.tableSortOrder()
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
