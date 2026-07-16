package com.rpb.reservation.i18n.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.i18n.application.I18nCatalogMessageCommand;
import com.rpb.reservation.i18n.application.I18nCatalogService;
import com.rpb.reservation.i18n.application.I18nCatalogServiceErrorCode;
import com.rpb.reservation.i18n.application.I18nCatalogServiceException;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import com.rpb.reservation.tenantadmin.api.TenantAdminScopeResolver;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/i18n/catalog")
public class TenantAdminI18nCatalogController {
    private final I18nCatalogService service;
    private final TenantAdminScopeResolver scopeResolver;

    public TenantAdminI18nCatalogController(I18nCatalogService service, TenantAdminScopeResolver scopeResolver) {
        this.service = service;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping
    public ResponseEntity<I18nCatalogResponse> getCatalog(@PathVariable UUID storeId) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(I18nCatalogResponse.from(service.tenantCatalog(scope)));
    }

    @PatchMapping
    public ResponseEntity<I18nCatalogResponse> updateCatalog(
        @PathVariable UUID storeId,
        @RequestBody(required = false) I18nCatalogRequests.TenantUpdateRequest request
    ) {
        StoreScope scope = scopeResolver.requireTenantAdminScope(storeId);
        return ResponseEntity.ok(I18nCatalogResponse.from(
            service.updateTenantCatalog(scope, scopeLevel(request), toCommands(request))
        ));
    }

    @ExceptionHandler(TenantAdminApiException.class)
    public ResponseEntity<I18nCatalogApiErrorResponse> handleTenantAdminException(TenantAdminApiException exception) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(I18nCatalogServiceException.class)
    public ResponseEntity<I18nCatalogApiErrorResponse> handleServiceException(I18nCatalogServiceException exception) {
        return apiError(toApiError(exception.code()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<I18nCatalogApiErrorResponse> handleDataAccessException(DataAccessException exception) {
        return apiError(I18nCatalogApiErrorCode.PERSISTENCE_ERROR);
    }

    private static String scopeLevel(I18nCatalogRequests.TenantUpdateRequest request) {
        return request == null ? null : request.scopeLevel();
    }

    private static List<I18nCatalogMessageCommand> toCommands(I18nCatalogRequests.TenantUpdateRequest request) {
        if (request == null || request.messages() == null) {
            return null;
        }
        return request.messages().stream()
            .map(message -> message == null ? null : message.toCommand())
            .toList();
    }

    private static ResponseEntity<I18nCatalogApiErrorResponse> apiError(I18nCatalogApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(I18nCatalogApiErrorResponse.of(code));
    }

    private static I18nCatalogApiErrorCode toApiError(TenantAdminApiErrorCode code) {
        return switch (code) {
            case UNAUTHENTICATED -> I18nCatalogApiErrorCode.UNAUTHENTICATED;
            case FORBIDDEN, STORE_SCOPE_MISMATCH -> I18nCatalogApiErrorCode.FORBIDDEN;
            default -> I18nCatalogApiErrorCode.REQUEST_INVALID;
        };
    }

    private static I18nCatalogApiErrorCode toApiError(I18nCatalogServiceErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> I18nCatalogApiErrorCode.REQUEST_INVALID;
            case KEY_NOT_ALLOWED -> I18nCatalogApiErrorCode.KEY_NOT_ALLOWED;
            case LOCALE_NOT_SUPPORTED -> I18nCatalogApiErrorCode.LOCALE_NOT_SUPPORTED;
            case PLACEHOLDER_UNKNOWN -> I18nCatalogApiErrorCode.PLACEHOLDER_UNKNOWN;
            case VERSION_CONFLICT -> I18nCatalogApiErrorCode.VERSION_CONFLICT;
        };
    }
}
