package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.StaffPermissionsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/staff/{staffId}/permissions")
public class StaffPermissionsController {
    public record Update(Long expectedVersion, List<String> permissions) {}
    private final TenantAdminScopeResolver scopes;
    private final StaffPermissionsService service;
    public StaffPermissionsController(TenantAdminScopeResolver scopes, StaffPermissionsService service) { this.scopes = scopes; this.service = service; }
    @GetMapping
    public ResponseEntity<StaffPermissionsService.View> get(@PathVariable UUID storeId, @PathVariable UUID staffId) {
        var scope = scopes.requireTenantAdminActorScope(storeId);
        return response(service.get(scope.scope(), scope.actor(), staffId));
    }
    @PutMapping
    public ResponseEntity<StaffPermissionsService.View> put(@PathVariable UUID storeId, @PathVariable UUID staffId,
        @RequestBody Update update, HttpServletRequest request) {
        var scope = scopes.requireTenantAdminActorScope(storeId);
        String site = request.getHeader("Sec-Fetch-Site");
        if (!"1".equals(request.getHeader("X-RPB-Staff-Permissions")) || "same-site".equals(site) || "cross-site".equals(site))
            throw new TenantAdminApiException(TenantAdminApiErrorCode.FORBIDDEN);
        if (update == null) throw new TenantAdminApiException(TenantAdminApiErrorCode.REQUEST_INVALID);
        return response(service.put(scope.scope(), scope.actor(), staffId, update.expectedVersion(), update.permissions()));
    }
    private ResponseEntity<StaffPermissionsService.View> response(StaffPermissionsService.View view) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(view);
    }
    @ExceptionHandler(TenantAdminApiException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> scopeError(TenantAdminApiException error) { return error(error.code()); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> invalid(Exception error) { return error(TenantAdminApiErrorCode.REQUEST_INVALID); }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> persistence(Exception error) { return error(TenantAdminApiErrorCode.PERSISTENCE_ERROR); }
    private ResponseEntity<TenantAdminApiErrorResponse> error(TenantAdminApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).header("Cache-Control", "private, no-store").body(TenantAdminApiErrorResponse.of(code));
    }
}
