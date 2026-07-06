package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.CustomerManagementApplicationService;
import com.rpb.reservation.customer.application.CustomerManagementCommand;
import com.rpb.reservation.customer.application.CustomerManagementError;
import com.rpb.reservation.customer.application.CustomerManagementException;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaServiceException;
import com.rpb.reservation.reservation.api.ReservationMealPeriodApiErrorCode;
import com.rpb.reservation.reservation.api.ReservationMealPeriodApiException;
import com.rpb.reservation.reservation.api.ReservationMealPeriodSettingsRequest;
import com.rpb.reservation.reservation.api.ReservationMealPeriodSettingsResponse;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodManagementService;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodServiceErrorCode;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodServiceException;
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
import com.rpb.reservation.tenantadmin.api.TenantAdminScopeResolver.TenantAdminActorScope;
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
    private static final String EXCEL_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TenantAdminStaffService staffService;
    private final TenantAdminTableService tableService;
    private final TenantAdminSettingsService settingsService;
    private final TenantAdminProfileService profileService;
    private final ReservationMealPeriodManagementService mealPeriodService;
    private final CustomerManagementApplicationService customerManagementService;
    private final TenantAdminScopeResolver scopeResolver;

    public TenantAdminController(
        TenantAdminStaffService staffService,
        TenantAdminTableService tableService,
        TenantAdminSettingsService settingsService,
        TenantAdminProfileService profileService,
        ReservationMealPeriodManagementService mealPeriodService,
        CustomerManagementApplicationService customerManagementService,
        TenantAdminScopeResolver scopeResolver
    ) {
        this.staffService = staffService;
        this.tableService = tableService;
        this.settingsService = settingsService;
        this.profileService = profileService;
        this.mealPeriodService = mealPeriodService;
        this.customerManagementService = customerManagementService;
        this.scopeResolver = scopeResolver;
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

    @GetMapping("/staff/me")
    public ResponseEntity<TenantAdminStaffResponse> getCurrentTenantAdminStaff(@PathVariable UUID storeId) {
        TenantAdminActorScope actorScope = scopeResolver.requireTenantAdminActorScope(storeId);
        return ResponseEntity.ok(TenantAdminStaffResponse.from(
            staffService.getCurrentTenantAdmin(actorScope.scope(), actorScope.actor().actorId())
        ));
    }

    @PatchMapping("/staff/me")
    public ResponseEntity<TenantAdminStaffResponse> updateCurrentTenantAdminStaff(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminStaffMutationRequest request
    ) {
        TenantAdminActorScope actorScope = scopeResolver.requireTenantAdminActorScope(storeId);
        return ResponseEntity.ok(TenantAdminStaffResponse.from(
            staffService.updateCurrentTenantAdmin(actorScope.scope(), actorScope.actor().actorId(), toCommand(request))
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

    @GetMapping("/customers")
    public ResponseEntity<TenantAdminCustomerListResponse> listCustomers(
        @PathVariable UUID storeId,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", required = false) String limit,
        @RequestParam(value = "offset", required = false) String offset
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminCustomerListResponse.from(
            customerManagementService.list(
                scope.tenantScope(),
                keyword,
                optionalInteger(limit),
                optionalInteger(offset)
            )
        ));
    }

    @PostMapping("/customers")
    public ResponseEntity<TenantAdminCustomerResponse> createCustomer(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminCustomerMutationRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.status(201).body(TenantAdminCustomerResponse.from(
            customerManagementService.create(scope.tenantScope(), toCommand(request))
        ));
    }

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<TenantAdminCustomerResponse> getCustomer(
        @PathVariable UUID storeId,
        @PathVariable UUID customerId
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminCustomerResponse.from(
            customerManagementService.get(scope.tenantScope(), customerId)
        ));
    }

    @PatchMapping("/customers/{customerId}")
    public ResponseEntity<TenantAdminCustomerResponse> updateCustomer(
        @PathVariable UUID storeId,
        @PathVariable UUID customerId,
        @RequestBody(required = false) TenantAdminCustomerMutationRequest request
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminCustomerResponse.from(
            customerManagementService.update(scope.tenantScope(), customerId, toCommand(request))
        ));
    }

    @PostMapping("/customers/{customerId}/archive")
    public ResponseEntity<TenantAdminCustomerArchiveResponse> archiveCustomer(
        @PathVariable UUID storeId,
        @PathVariable UUID customerId
    ) {
        StoreScope scope = requireTenantAdminScope(storeId);
        customerManagementService.archive(scope.tenantScope(), customerId);
        return ResponseEntity.ok(TenantAdminCustomerArchiveResponse.ok());
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

    @GetMapping("/reservation-meal-periods")
    public ResponseEntity<ReservationMealPeriodSettingsResponse> getReservationMealPeriods(@PathVariable UUID storeId) {
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(ReservationMealPeriodSettingsResponse.from(mealPeriodService.getStoreSettings(scope)));
    }

    @PatchMapping("/reservation-meal-periods")
    public ResponseEntity<ReservationMealPeriodSettingsResponse> updateReservationMealPeriods(
        @PathVariable UUID storeId,
        @RequestBody(required = false) ReservationMealPeriodSettingsRequest request
    ) {
        if (request == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        StoreScope scope = requireTenantAdminScope(storeId);
        return ResponseEntity.ok(ReservationMealPeriodSettingsResponse.from(mealPeriodService.updateStoreSettings(
            scope,
            request.usePlatformSeed(),
            request.copyPlatformSeed(),
            request.commands()
        )));
    }

    @ExceptionHandler(TenantAdminApiException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleApiException(TenantAdminApiException exception) {
        return apiError(exception.code());
    }

    @ExceptionHandler(TenantAdminServiceException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleServiceException(TenantAdminServiceException exception) {
        return apiError(TenantAdminApiErrorCode.valueOf(exception.code().name()));
    }

    @ExceptionHandler(CustomerManagementException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleCustomerManagementException(
        CustomerManagementException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(CallScreenMediaServiceException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleMediaServiceException(
        CallScreenMediaServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(ReservationMealPeriodServiceException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleMealPeriodServiceException(
        ReservationMealPeriodServiceException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(ReservationMealPeriodApiException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleMealPeriodApiException(
        ReservationMealPeriodApiException exception
    ) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(TenantAdminApiErrorCode.PERSISTENCE_ERROR);
    }

    private StoreScope requireTenantAdminScope(UUID storeId) {
        return scopeResolver.requireTenantAdminScope(storeId);
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

    private static TenantAdminApiErrorCode toApiError(ReservationMealPeriodServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> TenantAdminApiErrorCode.REQUEST_INVALID;
            case PERSISTENCE_ERROR -> TenantAdminApiErrorCode.PERSISTENCE_ERROR;
        };
    }

    private static TenantAdminApiErrorCode toApiError(ReservationMealPeriodApiErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> TenantAdminApiErrorCode.REQUEST_INVALID;
            case PERSISTENCE_ERROR -> TenantAdminApiErrorCode.PERSISTENCE_ERROR;
            case UNAUTHENTICATED, FORBIDDEN -> TenantAdminApiErrorCode.FORBIDDEN;
        };
    }

    private static TenantAdminApiErrorCode toApiError(CustomerManagementError code) {
        return switch (code) {
            case REQUEST_INVALID -> TenantAdminApiErrorCode.REQUEST_INVALID;
            case CUSTOMER_NOT_FOUND -> TenantAdminApiErrorCode.CUSTOMER_NOT_FOUND;
            case CUSTOMER_PHONE_CONFLICT -> TenantAdminApiErrorCode.CUSTOMER_PHONE_CONFLICT;
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

    private static CustomerManagementCommand toCommand(TenantAdminCustomerMutationRequest request) {
        if (request == null) {
            return null;
        }
        return new CustomerManagementCommand(
            request.displayName(),
            request.nickname(),
            request.phoneE164(),
            request.email()
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

    private static Integer optionalInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
    }
}
