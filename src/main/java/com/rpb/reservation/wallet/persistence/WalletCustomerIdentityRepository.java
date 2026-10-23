package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WalletCustomerIdentityRepository {
 private final JdbcTemplate jdbc;
 public WalletCustomerIdentityRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void issue(StoreScope scope,UUID program,UUID customer,String hash,Instant now,Instant expiry){
  UUID tenant=scope.tenantId().value();
  jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","wallet.identity:"+tenant+":"+program+":"+customer);
  jdbc.update("update wallet_customer_identity_codes set expires_at=? where tenant_id=? and program_id=? and customer_id=? and expires_at>?",Timestamp.from(now),tenant,program,customer,Timestamp.from(now));
  jdbc.update("insert into wallet_customer_identity_codes(tenant_id,program_id,customer_id,code_hash,created_at,expires_at) values(?,?,?,?,?,?)",tenant,program,customer,hash,Timestamp.from(now),Timestamp.from(expiry));
 }
 public UUID resolve(StoreScope scope,UUID program,String hash,Instant now){
  return jdbc.query("select customer_id from wallet_customer_identity_codes where tenant_id=? and program_id=? and code_hash=? and expires_at>?",(rs,n)->rs.getObject(1,UUID.class),scope.tenantId().value(),program,hash,Timestamp.from(now)).stream().findFirst().orElseThrow(WalletException::missing);
 }
}
