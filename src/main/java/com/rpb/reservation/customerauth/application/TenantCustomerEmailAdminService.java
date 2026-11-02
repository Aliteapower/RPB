package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant configuration commands and their audit evidence commit atomically; secrets never enter audit metadata. */
@Service
public class TenantCustomerEmailAdminService {
 private final CustomerAuthIntegrationAdminService settings;
 private final AuditLogRepositoryPort audit;
 public TenantCustomerEmailAdminService(CustomerAuthIntegrationAdminService settings,AuditLogRepositoryPort audit){this.settings=settings;this.audit=audit;}
 @Transactional(readOnly=true) public CustomerEmailSettings getEmailSettings(StoreScope scope){return settings.getEmailSettings(scope);}
 @Transactional(readOnly=true) public String emailSettingsScope(StoreScope scope){return settings.emailSettingsScope(scope);}
 @Transactional public CustomerEmailSettings saveEmailSettings(StoreScope scope,CustomerEmailSettingsCommand command,CurrentActor actor){
  if(actor==null||!scope.tenantId().value().equals(actor.tenantId())||!actor.canAccessStore(scope.storeId().value())||!actor.roles().contains("tenant_admin")||!actor.hasPermission("tenant.admin.manage"))throw new IllegalArgumentException("Invalid tenant email administrator");
  CustomerEmailSettings saved=settings.saveEmailSettings(scope,command);
  audit.append(scope.tenantScope(),new AuditLog(UUID.randomUUID(),"customer.email_settings.saved","tenant_customer_email_settings",scope.tenantId().value(),"staff","staff",actor.actorId(),"{\"enabled\":"+saved.enabled()+"}"));
  return saved;
 }
}
