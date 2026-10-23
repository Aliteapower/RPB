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
        var creditComponents=entryComponents(s,p,from,to,"topup_credit");
        var spentComponents=entryComponents(s,p,from,to,"bill_debit");
        var returnComponents=entryComponents(s,p,from,to,"bill_refund");
        var exitComponents=entryComponents(s,p,from,to,"topup_refund");
        var revokeComponents=entryComponents(s,p,from,to,"bonus_revoke");
        long cashPrincipalRefund=jdbc.queryForObject("select coalesce(sum(x.amount_cents),0) from wallet_refund_parts x join wallet_refunds r on r.id=x.refund_id and r.tenant_id=x.tenant_id where r.tenant_id=? and r.store_id=? and r.program_id=? and r.topup_id is not null and r.exception_collection_id is null and x.completed=true and x.updated_at>=? and x.updated_at<?",Long.class,s.tenantId().value(),s.storeId().value(),p.id(),Timestamp.from(from),Timestamp.from(to));
        var accountComponents=jdbc.queryForMap("select coalesce(sum(principal_balance_cents),0) as principal,coalesce(sum(bonus_balance_cents),0) as bonus,coalesce(sum(principal_reserved_cents),0) as principal_reserved,coalesce(sum(bonus_reserved_cents),0) as bonus_reserved from wallet_accounts where tenant_id=? and program_id=?",s.tenantId().value(),p.id());
        long componentMismatch=jdbc.queryForObject("select count(*) from wallet_accounts a where a.tenant_id=? and a.program_id=? and (a.balance_cents<>a.principal_balance_cents+a.bonus_balance_cents or a.reserved_cents<>a.principal_reserved_cents+a.bonus_reserved_cents or a.principal_reserved_cents>a.principal_balance_cents or a.bonus_reserved_cents>a.bonus_balance_cents or (a.principal_balance_cents::numeric,a.bonus_balance_cents::numeric) is distinct from (select row(coalesce(sum((case when e.kind in ('topup_credit','bill_refund') then 1 else -1 end)*coalesce(x.principal,e.amount_cents)),0),coalesce(sum((case when e.kind in ('topup_credit','bill_refund') then 1 else -1 end)*coalesce(x.bonus,0)),0)) from wallet_entries e left join lateral (select sum(y.principal_cents) as principal,sum(y.bonus_cents) as bonus from wallet_entry_allocations y where y.tenant_id=e.tenant_id and y.entry_id=e.id) x on true where e.tenant_id=a.tenant_id and e.account_id=a.id))",Long.class,s.tenantId().value(),p.id());
        long fundingMismatch=jdbc.queryForObject("select count(*) from wallet_accounts a where a.tenant_id=? and a.program_id=? and ((a.principal_balance_cents::numeric,a.bonus_balance_cents::numeric,a.principal_reserved_cents::numeric,a.bonus_reserved_cents::numeric) is distinct from (select row(coalesce(sum(l.principal),0),coalesce(sum(l.bonus),0),coalesce(sum(l.reserved_principal),0),coalesce(sum(l.reserved_bonus),0)) from wallet_funding_lots l where l.tenant_id=a.tenant_id and l.account_id=a.id) or exists(select 1 from wallet_entries e left join wallet_entry_allocations x on x.entry_id=e.id and x.tenant_id=e.tenant_id where e.tenant_id=a.tenant_id and e.account_id=a.id and e.account_version>a.funding_cutover_version group by e.id,e.amount_cents having coalesce(sum(x.principal_cents+x.bonus_cents),0)<>e.amount_cents))",Long.class,s.tenantId().value(),p.id());
        return new Reconciliation(cash,paynow,card,spent,external,refunded,pending,due,balance,reserved,exception,credited,pendingRefund,mismatches,collections.period(s,from,to),
            creditComponents[0],creditComponents[1],spentComponents[0],spentComponents[1],returnComponents[0],returnComponents[1],cashPrincipalRefund,exitComponents[1]+revokeComponents[1],
            number(accountComponents,"principal"),number(accountComponents,"bonus"),number(accountComponents,"principal_reserved"),number(accountComponents,"bonus_reserved"),componentMismatch,fundingMismatch);
    }
    /** Historical entries without allocations are entirely principal; new entries use their frozen allocations. */
    private long[] entryComponents(StoreScope s,Program p,Instant from,Instant to,String kind) {
        return jdbc.queryForObject("select coalesce(sum(coalesce(x.principal,e.amount_cents)),0),coalesce(sum(coalesce(x.bonus,0)),0) from wallet_entries e left join (select tenant_id,entry_id,sum(principal_cents) as principal,sum(bonus_cents) as bonus from wallet_entry_allocations where tenant_id=? and program_id=? group by tenant_id,entry_id) x on x.tenant_id=e.tenant_id and x.entry_id=e.id where e.tenant_id=? and e.store_id=? and e.program_id=? and e.kind=? and e.created_at>=? and e.created_at<?",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},s.tenantId().value(),p.id(),s.tenantId().value(),s.storeId().value(),p.id(),kind,Timestamp.from(from),Timestamp.from(to));
    }
    private long number(java.util.Map<String,Object> values,String key){return ((Number)values.get(key)).longValue();}

}
