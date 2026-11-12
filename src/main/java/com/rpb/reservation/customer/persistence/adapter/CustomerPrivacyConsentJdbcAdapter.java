package com.rpb.reservation.customer.persistence.adapter;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customer.application.port.out.CustomerPrivacyConsentPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerPrivacyConsentJdbcAdapter implements CustomerPrivacyConsentPort {
 private final JdbcTemplate jdbc;
 public CustomerPrivacyConsentJdbcAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void append(StoreScope scope,UUID customer,UUID actor,String source,CustomerPrivacyNotice notice){
  jdbc.update("insert into customer_privacy_consents(id,tenant_id,customer_id,store_id,accepted,recorded_by,recorded_by_name,entry_source,confirmation_method,notice_version,notice_text_zh,notice_text_en) values(?,?,?,?,true,?,(select coalesce(nullif(display_name,''),username) from auth_accounts where id=? and tenant_id=?),?,'merchant_attestation',?,?,?)",UUID.randomUUID(),scope.tenantId().value(),customer,scope.storeId().value(),actor,actor,scope.tenantId().value(),source,notice.version(),notice.textZh(),notice.textEn());
 }
 public List<CustomerPrivacyConsentRecord> list(TenantScope scope,UUID customer){
  return jdbc.query("select * from customer_privacy_consents where tenant_id=? and customer_id=? order by recorded_at desc,id desc",(r,n)->new CustomerPrivacyConsentRecord(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),r.getObject("store_id",UUID.class),r.getBoolean("accepted"),r.getObject("recorded_by",UUID.class),r.getObject("recorded_by_customer_id",UUID.class),r.getString("recorded_by_name"),r.getObject("recorded_at",OffsetDateTime.class),r.getString("entry_source"),r.getString("confirmation_method"),r.getString("notice_version"),r.getString("notice_text_zh"),r.getString("notice_text_en")),scope.tenantId().value(),customer);
 }
 public void appendSelfRegistration(StoreScope scope,UUID customer,CustomerPrivacyNotice notice){
  jdbc.update("insert into customer_privacy_consents(id,tenant_id,customer_id,store_id,accepted,recorded_by_customer_id,recorded_by_name,entry_source,confirmation_method,notice_version,notice_text_zh,notice_text_en) values(?,?,?,?,true,?,(select coalesce(nullif(display_name,''),phone_e164) from customers where id=? and tenant_id=?),'customer_registration','customer_registration_submission',?,?,?)",UUID.randomUUID(),scope.tenantId().value(),customer,scope.storeId().value(),customer,customer,scope.tenantId().value(),notice.version(),notice.textZh(),notice.textEn());
 }
}
