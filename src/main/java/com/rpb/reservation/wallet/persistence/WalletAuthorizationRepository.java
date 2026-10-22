package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.WalletException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Repository
public class WalletAuthorizationRepository {
    private final JdbcTemplate jdbc;
    public WalletAuthorizationRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public void code(StoreScope scope,UUID account,String hash,Instant now,Instant expires) {
        jdbc.update("update wallet_payment_codes set expires_at=? where tenant_id=? and account_id=? and bill_id is null and expires_at>?",Timestamp.from(now),scope.tenantId().value(),account,Timestamp.from(now));
        jdbc.update("insert into wallet_payment_codes(tenant_id,account_id,code_hash,expires_at) values(?,?,?,?)",scope.tenantId().value(),account,hash,Timestamp.from(expires));
    }
    public void bind(StoreScope scope,UUID bill,UUID account,String hash,Instant now) {
        var rows=jdbc.queryForList("select id,bill_id,expires_at from wallet_payment_codes where tenant_id=? and account_id=? and code_hash=? for update",scope.tenantId().value(),account,hash);
        if(rows.isEmpty()) throw WalletException.missing();
        var row=rows.getFirst();
        if(!((Timestamp)row.get("expires_at")).toInstant().isAfter(now)) throw new WalletException("WALLET_AUTHORIZATION_EXPIRED",409,"付款码已过期");
        if(row.get("bill_id")!=null && !bill.equals(row.get("bill_id"))) throw WalletException.conflict("付款码已经绑定其他账单");
        jdbc.update("update wallet_payment_codes set bill_id=? where id=? and tenant_id=?",bill,row.get("id"),scope.tenantId().value());
    }
    /** Caller locks the account first, matching payment-code regeneration lock order. */
    public Instant lockUnboundCode(StoreScope scope,UUID account,String hash) {
        var rows=jdbc.queryForList("select bill_id,expires_at from wallet_payment_codes where tenant_id=? and account_id=? and code_hash=? for update",scope.tenantId().value(),account,hash);
        if(rows.isEmpty())throw WalletException.missing();
        var row=rows.getFirst();
        if(row.get("bill_id")!=null)throw WalletException.conflict("付款码已经绑定其他账单");
        return ((Timestamp)row.get("expires_at")).toInstant();
    }
    public UUID resolve(StoreScope scope,String hash,Instant now) {
        return jdbc.query("select account_id from wallet_payment_codes where tenant_id=? and code_hash=? and expires_at>? and bill_id is null",
            (rs,n)->rs.getObject("account_id",UUID.class),scope.tenantId().value(),hash,Timestamp.from(now)).stream().findFirst().orElseThrow(WalletException::missing);
    }
    public Quote quote(StoreScope scope,Bill bill,Account account,long amount,UUID customer,Instant expires) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into wallet_authorizations(id,tenant_id,bill_id,account_id,amount_cents,account_version,bill_version,customer_id,expires_at) values(?,?,?,?,?,?,?,?,?)",id,scope.tenantId().value(),bill.id(),account.id(),amount,account.version(),bill.version(),customer,Timestamp.from(expires));
        return new Quote(id,bill.id(),account.id(),amount,bill.remainingCents()-amount,account.version(),bill.version(),expires);
    }
    public Authorization authorization(StoreScope scope,UUID id,UUID bill,UUID customer) {
        return jdbc.query("select * from wallet_authorizations where tenant_id=? and id=? and bill_id=? and customer_id=? for update",
            (rs,n)->new Authorization(new Quote(rs.getObject("id",UUID.class),rs.getObject("bill_id",UUID.class),rs.getObject("account_id",UUID.class),rs.getLong("amount_cents"),0,rs.getInt("account_version"),rs.getInt("bill_version"),rs.getTimestamp("expires_at").toInstant()),rs.getTimestamp("consumed_at")!=null),scope.tenantId().value(),id,bill,customer).stream().findFirst().orElseThrow(WalletException::missing);
    }
    public void consume(StoreScope scope,UUID id,Instant now) {
        if(jdbc.update("update wallet_authorizations set consumed_at=? where tenant_id=? and id=? and consumed_at is null",Timestamp.from(now),scope.tenantId().value(),id)!=1) throw WalletException.conflict("授权已使用");
    }
    public record Authorization(Quote quote,boolean consumed) {}
}
