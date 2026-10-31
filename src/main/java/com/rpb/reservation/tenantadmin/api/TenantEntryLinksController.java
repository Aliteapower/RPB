package com.rpb.reservation.tenantadmin.api;

import com.rpb.reservation.tenantadmin.application.*;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/tenant-admin/entry-links")
public class TenantEntryLinksController {
    private final TenantAdminScopeResolver scopeResolver;
    private final TenantEntryLinksService service;
    public TenantEntryLinksController(TenantAdminScopeResolver scopeResolver,TenantEntryLinksService service){this.scopeResolver=scopeResolver;this.service=service;}
    @GetMapping
    public ResponseEntity<TenantEntryLinksContext> get(@PathVariable UUID storeId) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(service.get(scopeResolver.requireTenantAdminScope(storeId)));
    }
    @ExceptionHandler(TenantAdminApiException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> scopeError(TenantAdminApiException e){return error(e.code());}
    @ExceptionHandler(TenantAdminServiceException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> serviceError(TenantAdminServiceException e){return error(TenantAdminApiErrorCode.valueOf(e.code().name()));}
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<TenantAdminApiErrorResponse> databaseError(DataAccessException e){return error(TenantAdminApiErrorCode.PERSISTENCE_ERROR);}
    private ResponseEntity<TenantAdminApiErrorResponse> error(TenantAdminApiErrorCode code){return ResponseEntity.status(code.httpStatus()).body(TenantAdminApiErrorResponse.of(code));}
}
