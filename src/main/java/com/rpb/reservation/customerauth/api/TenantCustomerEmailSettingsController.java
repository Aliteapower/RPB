package com.rpb.reservation.customerauth.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.tenantadmin.api.*;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/stores/{storeId}/tenant-admin/customer-auth/email-settings",
 "/api/v1/stores/{storeId}/tenant-admin/public-booking/customer-auth/email-settings"})
public class TenantCustomerEmailSettingsController {
 private final TenantCustomerEmailAdminService service;
 private final TenantAdminScopeResolver scopes;
 private final CustomerEmailManagementAccess access;
 public TenantCustomerEmailSettingsController(TenantCustomerEmailAdminService service,TenantAdminScopeResolver scopes,CustomerEmailManagementAccess access){this.service=service;this.scopes=scopes;this.access=access;}
 private TenantAdminScopeResolver.TenantAdminActorScope authorized(UUID store){var resolved=scopes.requireTenantAdminActorScope(store);access.require(resolved.scope());return resolved;}
 @GetMapping public SettingsResponse get(@PathVariable UUID storeId){StoreScope s=authorized(storeId).scope();return SettingsResponse.from(service.getEmailSettings(s),service.emailSettingsScope(s));}
 @PutMapping public SettingsResponse save(@PathVariable UUID storeId,@RequestBody(required=false) SettingsRequest request){var s=authorized(storeId);return SettingsResponse.from(service.saveEmailSettings(s.scope(),request==null?null:request.command(),s.actor()),"tenant");}
 public record SettingsRequest(Boolean enabled,String fromEmail,String fromName,String smtpHost,Integer smtpPort,String smtpUsername,String smtpPassword,Boolean smtpStartTls){
  CustomerEmailSettingsCommand command(){return new CustomerEmailSettingsCommand(enabled,fromEmail,fromName,smtpHost,smtpPort,smtpUsername,smtpPassword,smtpStartTls);}
 }
 public record SettingsResponse(boolean success,boolean enabled,String provider,String fromEmail,String fromName,String smtpHost,int smtpPort,String smtpUsername,boolean smtpStartTls,boolean secretConfigured,String configurationScope){
  static SettingsResponse from(CustomerEmailSettings s,String scope){return new SettingsResponse(true,s.enabled(),s.provider(),s.fromEmail(),s.fromName(),s.smtpHost(),s.smtpPort(),s.smtpUsername(),s.smtpStartTls(),s.secretConfigured(),scope);}
 }
 @ExceptionHandler(TenantAdminApiException.class) public ResponseEntity<TenantAdminApiErrorResponse> error(TenantAdminApiException e){return ResponseEntity.status(e.code().httpStatus()).body(TenantAdminApiErrorResponse.of(e.code()));}
 @ExceptionHandler({IllegalArgumentException.class,NullPointerException.class}) public ResponseEntity<TenantAdminApiErrorResponse> invalid(RuntimeException e){return ResponseEntity.badRequest().body(TenantAdminApiErrorResponse.of(TenantAdminApiErrorCode.REQUEST_INVALID));}
 @ExceptionHandler(DataAccessException.class) public ResponseEntity<TenantAdminApiErrorResponse> persistence(DataAccessException e){return ResponseEntity.internalServerError().body(TenantAdminApiErrorResponse.of(TenantAdminApiErrorCode.PERSISTENCE_ERROR));}
}
