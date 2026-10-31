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
 public void revokePayments(StoreScope scope,UUID program,UUID customer){
  jdbc.update("update wallet_customer_identity_codes set payment_enabled=false where tenant_id=? and program_id=? and customer_id=?",scope.tenantId().value(),program,customer);
 }
 public UUID resolve(StoreScope scope,UUID program,String hash,Instant now){
  var rows=jdbc.query("select customer_id,expires_at from wallet_customer_identity_codes where tenant_id=? and program_id=? and code_hash=?",(rs,n)->new Identity(rs.getObject(1,UUID.class),rs.getTimestamp(2).toInstant()),scope.tenantId().value(),program,hash);
  var code=rows.stream().findFirst().orElseThrow(WalletException::missing);
  if(!code.expiresAt().isAfter(now))throw new WalletException("WALLET_PERSONAL_CODE_EXPIRED",409,"个人码已过期或已刷新，请顾客重新出示当前个人钱包码；相册旧图片不能继续使用");
  return code.customerId();
 }
 private record Identity(UUID customerId,Instant expiresAt) {}
}
