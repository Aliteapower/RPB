package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.appgate.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/product-lines")
public class TenantAdminProductLinesController {
    private final TenantAdminScopeResolver scopes;
    private final StoreProductSettingsService service;
    public TenantAdminProductLinesController(TenantAdminScopeResolver scopes,StoreProductSettingsService service) {
        this.scopes=scopes; this.service=service;
    }
    public record Products(boolean success,List<StoreProductSetting> products) {}
    public record Update(@NotNull Boolean enabled) {}
    @GetMapping public ResponseEntity<Products> list(@PathVariable UUID storeId) {
        return response(service.list(scopes.requireTenantAdminScope(storeId)));
    }
    @PutMapping("/{appKey}") public ResponseEntity<Products> update(@PathVariable UUID storeId,@PathVariable String appKey,
        @Valid @RequestBody Update update,HttpServletRequest request) {
        var scope=scopes.requireTenantAdminActorScope(storeId);
        String site=request.getHeader("Sec-Fetch-Site");
        if (!"1".equals(request.getHeader("X-RPB-Product-Settings")) || "cross-site".equals(site) || "same-site".equals(site))
            throw new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN);
        return response(List.of(service.setEnabled(scope.scope(),scope.actor(),appKey,update.enabled())));
    }
    private ResponseEntity<Products> response(List<StoreProductSetting> products) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(new Products(true,products));
    }
    @ExceptionHandler(TenantAdminApiException.class) public ResponseEntity<TenantAdminApiErrorResponse> scopeError(TenantAdminApiException e) { return error(e.code()); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<TenantAdminApiErrorResponse> invalidRequest(Exception e) { return error(TenantAdminApiErrorCode.REQUEST_INVALID); }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> persistenceError(DataAccessException e) { return error(TenantAdminApiErrorCode.PERSISTENCE_ERROR); }
    @ExceptionHandler(StoreProductSettingsException.class) public ResponseEntity<TenantAdminApiErrorResponse> productError(StoreProductSettingsException e) {
        return error(switch(e.reason()) {
            case INVALID_PRODUCT -> TenantAdminApiErrorCode.REQUEST_INVALID;
            case STORE_NOT_FOUND -> TenantAdminApiErrorCode.TENANT_PROFILE_NOT_FOUND;
            case UNAVAILABLE -> TenantAdminApiErrorCode.PRODUCT_LINE_UNAVAILABLE;
            case FORBIDDEN -> TenantAdminApiErrorCode.FORBIDDEN;
        });
    }
    private ResponseEntity<TenantAdminApiErrorResponse> error(TenantAdminApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).header("Cache-Control","private, no-store").body(TenantAdminApiErrorResponse.of(code));
    }
}
