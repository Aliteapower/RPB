package com.rpb.reservation.wallet.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Repository
public class WalletReconciliationRepository {
    private final JdbcTemplate jdbc;
    private final WalletCollectionRepository collections;
    public WalletReconciliationRepository(JdbcTemplate jdbc,WalletCollectionRepository collections) {this.jdbc=jdbc;this.collections=collections;}
    private long total(StoreScope s,Instant from,Instant to,String table,String expression,String extra) {
        return jdbc.queryForObject("select coalesce(sum("+expression+"),0) from "+table+" where tenant_id=? and store_id=? and created_at>=? and created_at<? "+extra,Long.class,s.tenantId().value(),s.storeId().value(),Timestamp.from(from),Timestamp.from(to));
    }
    public Reconciliation query(StoreScope s,Program p,Instant from,Instant to) {
        long cash=total(s,from,to,"wallet_collections","amount_cents","and is_exception=false and topup_id is not null and channel='cash'");
        long paynow=total(s,from,to,"wallet_collections","amount_cents","and is_exception=false and topup_id is not null and channel='paynow'");
        long card=total(s,from,to,"wallet_collections","amount_cents","and is_exception=false and topup_id is not null and channel='credit_card'");
        long spent=total(s,from,to,"wallet_entries","amount_cents","and kind='bill_debit'");
        long external=total(s,from,to,"wallet_collections","amount_cents","and is_exception=false and bill_id is not null");
        long refunded=jdbc.queryForObject("select coalesce(sum(p.amount_cents),0) from wallet_refund_parts p join wallet_refunds r on r.id=p.refund_id and r.tenant_id=p.tenant_id where r.tenant_id=? and r.store_id=? and p.completed=true and p.updated_at>=? and p.updated_at<?",Long.class,s.tenantId().value(),s.storeId().value(),Timestamp.from(from),Timestamp.from(to))
            +total(s,from,to,"wallet_entries","amount_cents","and kind='bill_refund'");
        long exception=total(s,from,to,"wallet_collections","amount_cents","and is_exception=true");
        long credited=total(s,from,to,"wallet_entries","amount_cents","and kind='topup_credit'");
        long pendingRefund=jdbc.queryForObject("select coalesce(sum(p.amount_cents),0) from wallet_refund_parts p join wallet_refunds r on r.id=p.refund_id and r.tenant_id=p.tenant_id where r.tenant_id=? and r.store_id=? and r.status='pending' and p.completed=false and r.created_at>=? and r.created_at<?",Long.class,s.tenantId().value(),s.storeId().value(),Timestamp.from(from),Timestamp.from(to));
        long pending=total(s,from,to,"wallet_topups","amount_cents","and status in ('pending_collection','verified_pending_credit','exception')");
        long due=total(s,from,to,"wallet_bills","amount_cents-wallet_paid_cents-external_paid_cents","and closed=false");
        long balance=jdbc.queryForObject("select coalesce(sum(balance_cents),0) from wallet_accounts where tenant_id=? and program_id=?",Long.class,s.tenantId().value(),p.id());
        long reserved=jdbc.queryForObject("select coalesce(sum(reserved_cents),0) from wallet_accounts where tenant_id=? and program_id=?",Long.class,s.tenantId().value(),p.id());
        long mismatches=jdbc.queryForObject("select count(*) from wallet_accounts a where a.tenant_id=? and a.program_id=? and a.balance_cents<>(select coalesce(sum(case when e.kind in ('topup_credit','bill_refund') then e.amount_cents else -e.amount_cents end),0) from wallet_entries e where e.tenant_id=a.tenant_id and e.account_id=a.id)",Long.class,s.tenantId().value(),p.id());
        return new Reconciliation(cash,paynow,card,spent,external,refunded,pending,due,balance,reserved,exception,credited,pendingRefund,mismatches,collections.period(s,from,to));
    }
}
