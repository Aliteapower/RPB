package com.rpb.reservation.wallet.personalpayment;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.personalpayment.PersonalPaymentDtos.*;

/** Owns authorization state only. Actual debit stays in funding/account/bill modules. */
@Repository
public class WalletPersonalPaymentRepository {
 private final JdbcTemplate jdbc;
 public WalletPersonalPaymentRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 // Policy and consent mutations serialize with issuance/debit across every store of the issuer.
 public void lockProgram(StoreScope s,UUID program){lock("wallet.personal:"+s.tenantId().value()+":"+program);}
 public void lockIdentity(StoreScope s,UUID program,UUID customer){lock("wallet.identity:"+s.tenantId().value()+":"+program+":"+customer);}
 private void lock(String key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",key);}
 public Consent consent(StoreScope s,UUID p,UUID customer){
  var rows=jdbc.queryForList("select * from wallet_personal_payment_consents where tenant_id=? and program_id=? and customer_id=?",s.tenantId().value(),p,customer);
  Long accepted=null;boolean enabled=false;int version=0;
  if(!rows.isEmpty()){var row=rows.getFirst();accepted=(Long)row.get("limit_cents");version=((Number)row.get("policy_version")).intValue();enabled=Boolean.TRUE.equals(row.get("accepted"))&&((Number)row.get("rule_version")).intValue()==1&&accepted!=null;}
  return new Consent(accepted!=null,version,accepted,accepted,enabled,!enabled,1);
 }
 public void consent(StoreScope s,UUID p,UUID customer,ConsentUpdate c){jdbc.update("insert into wallet_personal_payment_consents(tenant_id,program_id,customer_id,accepted,limit_cents,policy_version,rule_version) values(?,?,?,?,?,?,1) on conflict(tenant_id,program_id,customer_id) do update set accepted=excluded.accepted,limit_cents=excluded.limit_cents,policy_version=excluded.policy_version,rule_version=1,updated_at=now()",s.tenantId().value(),p,customer,c.accepted(),c.limitCents(),c.policyVersion());}
 public void snapshot(StoreScope s,String hash,Consent c){jdbc.update("update wallet_customer_identity_codes set payment_enabled=?,payment_limit_cents=?,payment_policy_version=? where tenant_id=? and code_hash=?",c.paymentEnabled(),c.paymentEnabled()?c.limitCents():null,c.policyVersion(),s.tenantId().value(),hash);}
 public record Code(UUID customerId,Instant expiresAt,boolean enabled,Long limit,UUID billId,UUID storeId) {}
 public Code code(StoreScope s,UUID program,String hash){return jdbc.query("select customer_id,expires_at,payment_enabled,payment_limit_cents,payment_bill_id,payment_store_id from wallet_customer_identity_codes where tenant_id=? and program_id=? and code_hash=?",(rs,n)->new Code(rs.getObject(1,UUID.class),rs.getTimestamp(2).toInstant(),rs.getBoolean(3),rs.getObject(4,Long.class),rs.getObject(5,UUID.class),rs.getObject(6,UUID.class)),s.tenantId().value(),program,hash).stream().findFirst().orElseThrow(WalletException::missing);}
 public void bind(StoreScope s,String hash,UUID bill,String description){int n=jdbc.update("update wallet_customer_identity_codes set payment_bill_id=?,payment_store_id=?,payment_description=? where tenant_id=? and code_hash=? and payment_bill_id is null",bill,s.storeId().value(),description,s.tenantId().value(),hash);if(n!=1)throw new WalletException("WALLET_PERSONAL_CODE_USED",409,"个人码已用于付款，请顾客刷新后重新出示");}
}
