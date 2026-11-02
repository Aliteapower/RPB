package com.rpb.reservation.customerauth.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerEmailSettings;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Tenant-owned SMTP, with explicit legacy store fallback until an administrator resolves conflicts. */
final class TenantCustomerEmailSettingsJdbc {
 private final JdbcTemplate jdbc;
 TenantCustomerEmailSettingsJdbc(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private record Resolved(CustomerEmailSettings settings,String scope){}
 private Optional<Resolved> resolve(UUID tenant,UUID store){
  return jdbc.query("""
   select c.*, 'tenant' as configuration_scope
   from tenant_customer_email_settings c join stores s on s.tenant_id=c.tenant_id
   where c.tenant_id=? and s.id=? and s.deleted_at is null
   union all
   select c.tenant_id,c.enabled,c.provider,c.from_email,c.from_name,c.smtp_host,c.smtp_port,
          c.smtp_username,c.smtp_password_secret,c.smtp_start_tls,c.created_at,c.updated_at,c.version,
          'legacy_store' as configuration_scope
   from store_customer_email_settings c join stores s on s.tenant_id=c.tenant_id and s.id=c.store_id
   where c.tenant_id=? and c.store_id=? and c.deleted_at is null and s.deleted_at is null
     and not exists(select 1 from tenant_customer_email_settings t where t.tenant_id=c.tenant_id)
   """,(rs,n)->new Resolved(read(rs),rs.getString("configuration_scope")),tenant,store,tenant,store).stream().findFirst();
 }
 Optional<CustomerEmailSettings> find(UUID tenant,UUID store){return resolve(tenant,store).map(Resolved::settings);}
 String scope(UUID tenant,UUID store){return resolve(tenant,store).map(Resolved::scope).orElse("unconfigured");}
 CustomerEmailSettings save(StoreScope scope,CustomerEmailSettings s){
  int changed=jdbc.update("""
   insert into tenant_customer_email_settings
    (tenant_id,enabled,provider,from_email,from_name,smtp_host,smtp_port,smtp_username,smtp_password_secret,smtp_start_tls)
   select ?,?,?,?,?,?,?,?,?,? from stores where tenant_id=? and id=? and deleted_at is null
   on conflict (tenant_id) do update set enabled=excluded.enabled, provider=excluded.provider,
    from_email=excluded.from_email,from_name=excluded.from_name,smtp_host=excluded.smtp_host,
    smtp_port=excluded.smtp_port,smtp_username=excluded.smtp_username,smtp_password_secret=excluded.smtp_password_secret,
    smtp_start_tls=excluded.smtp_start_tls,updated_at=now(),version=tenant_customer_email_settings.version+1
   """,scope.tenantId().value(),s.enabled(),s.provider(),s.fromEmail(),s.fromName(),s.smtpHost(),s.smtpPort(),s.smtpUsername(),s.smtpPassword(),s.smtpStartTls(),scope.tenantId().value(),scope.storeId().value());
  if(changed!=1)throw new IllegalArgumentException("Invalid email settings scope");
  return find(scope.tenantId().value(),scope.storeId().value()).orElseThrow();
 }
 private static CustomerEmailSettings read(ResultSet r)throws SQLException{
  String password=r.getString("smtp_password_secret");
  return new CustomerEmailSettings(r.getBoolean("enabled"),r.getString("provider"),r.getString("from_email"),r.getString("from_name"),r.getString("smtp_host"),r.getInt("smtp_port"),r.getString("smtp_username"),password,r.getBoolean("smtp_start_tls"),password!=null&&!password.isBlank());
 }
}
