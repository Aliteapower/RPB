package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantAdminShareProfileCommand;
import com.rpb.reservation.tenantadmin.application.TenantAdminShareProfileService;
import com.rpb.reservation.tenantadmin.application.TenantAdminServiceException;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/share-profile")
public class TenantAdminShareProfileController {
    private final TenantAdminShareProfileService service;
    private final TenantAdminScopeResolver scopeResolver;

    public TenantAdminShareProfileController(
        TenantAdminShareProfileService service,
        TenantAdminScopeResolver scopeResolver
    ) {
        this.service = service;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping
    public ResponseEntity<TenantAdminShareProfileResponse> getShareProfile(@PathVariable UUID storeId) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminShareProfileResponse.from(service.getProfile(scope)));
    }

    @PatchMapping
    public ResponseEntity<TenantAdminShareProfileResponse> updateShareProfile(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminShareProfileRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminShareProfileResponse.from(service.updateProfile(scope, toCommand(request))));
    }

    @PostMapping("/preview")
    public ResponseEntity<TenantAdminSharePreviewResponse> previewShareProfile(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminShareProfileRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminSharePreviewResponse.from(service.preview(scope, toCommand(request))));
    }

    @PatchMapping("/template")
    public ResponseEntity<TenantAdminShareProfileResponse> updateShareTemplate(
        @PathVariable UUID storeId,
        @RequestBody(required = false) TenantAdminShareTemplateRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        String template = request == null ? null : request.reservationShareTemplate();
        return ResponseEntity.ok(TenantAdminShareProfileResponse.from(service.updateTemplate(scope, template)));
    }

    @PostMapping("/default-template")
    public ResponseEntity<TenantAdminShareProfileResponse> restoreDefaultTemplate(@PathVariable UUID storeId) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(TenantAdminShareProfileResponse.from(service.restoreDefaultTemplate(scope)));
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

    private static TenantAdminShareProfileCommand toCommand(TenantAdminShareProfileRequest request) {
        if (request == null) {
            return null;
        }
        return new TenantAdminShareProfileCommand(
            request.shareDisplayName(),
            request.googleMapUrl(),
            request.shareEmail(),
            request.whatsappBusinessPhoneE164(),
            request.reservationShareNote(),
            request.reservationShareTemplate()
        );
    }

    private static ResponseEntity<TenantAdminApiErrorResponse> apiError(TenantAdminApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(TenantAdminApiErrorResponse.of(code));
    }
}
