package com.rpb.reservation.wallet.transactions;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.transactions.TransactionViews.*;

@Repository
public class TransactionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public TransactionRepository(JdbcTemplate jdbc){this.jdbc=new NamedParameterJdbcTemplate(jdbc);}
    public List<Store> stores(UUID tenant,Set<UUID> staffStores,UUID customer,UUID context) {
        var p=new MapSqlParameterSource("tenant",tenant).addValue("context",context);
        String where;
        if(customer!=null){p.addValue("customer",customer);where="(s.id=:context or exists(select 1 from records r where r.store_id=s.id and r.customer_id=:customer))";}
        else {p.addValue("stores",staffStores.isEmpty()?List.of(new UUID(0,0)):staffStores);where="s.id in (:stores)";}
        return jdbc.query(TransactionProjection.CTE+"select s.id,s.display_name,s.timezone from stores s where s.tenant_id=:tenant and "+where+" order by s.display_name,s.id",p,(r,n)->new Store(r.getObject(1,UUID.class),r.getString(2),r.getString(3)));
    }
    public List<Item> items(String where,MapSqlParameterSource p,boolean customer,int limit) {
        return jdbc.query(TransactionProjection.CTE+"select * from records where "+where+" order by occurred_at desc,source_type desc,id desc limit "+limit,p,mapper(customer));
    }
    public record Totals(long count,Summary summary) {}
    public Totals totals(String where,MapSqlParameterSource p) {
        return jdbc.queryForObject(TransactionProjection.CTE+"""
          select count(*) total,
          coalesce(sum(wallet_paid+external_paid) filter(where status='completed' and type='payment'),0) consumption,
          coalesce(sum(wallet_paid) filter(where status='completed' and type='payment'),0) wp,
          coalesce(sum(external_paid) filter(where status='completed' and type='payment'),0) ep,
          coalesce(sum(principal) filter(where status='completed' and type='topup'),0) tp,
          coalesce(sum(bonus) filter(where status='completed' and type='topup'),0) tb,
          coalesce(sum(credit) filter(where status='completed' and type='topup'),0) tc,
          coalesce(sum(amount_cents) filter(where status='completed' and type='refund'),0) rf,
          coalesce(sum(amount_cents) filter(where status='completed' and source_type='money_reversal'),0) mr,
          coalesce(sum(quantity) filter(where type='points_grant'),0) pg,
          coalesce(sum(quantity) filter(where type='points_deduct'),0) pd,
          coalesce(sum(quantity) filter(where type='points_redemption'),0) pr,
          coalesce(sum(quantity) filter(where source_type='points' and type='reversal'),0) pv,
          coalesce(sum(quantity) filter(where source_type='points' and type='reversal' and direction='in'),0) pi,
          coalesce(sum(quantity) filter(where source_type='points' and type='reversal' and direction='out'),0) po
          from records where
          """+where,p,(r,n)->new Totals(r.getLong("total"),new Summary(r.getLong("consumption"),r.getLong("wp"),r.getLong("ep"),r.getLong("tp"),r.getLong("tb"),r.getLong("tc"),r.getLong("rf"),r.getLong("mr"),r.getLong("pg"),r.getLong("pd"),r.getLong("pr"),r.getLong("pv"),r.getLong("pi"),r.getLong("po"))));
    }
    public List<Event> evidence(UUID tenant,UUID store,String source,UUID id) {
        var p=new MapSqlParameterSource("tenant",tenant).addValue("store",store).addValue("id",id);
        // Source ownership is established by service before this query. Only immutable actions/effective part completion.
        String sql="""
          select 'action' kind,created_at at,action label from wallet_audit where tenant_id=:tenant and store_id=:store and source_id=:id
          union all select 'wallet_posted',created_at,kind from wallet_entries where tenant_id=:tenant and store_id=:store and source_id=:id
          union all select 'collection',created_at,'线下实收确认' from wallet_collections where tenant_id=:tenant and store_id=:store and not is_exception and (bill_id=:id or topup_id=:id)
          union all select 'refund_part_completed',p.updated_at,'外部退款完成' from wallet_refund_parts p join wallet_refunds r on r.id=p.refund_id and r.tenant_id=p.tenant_id where p.tenant_id=:tenant and r.store_id=:store and p.refund_id=:id and p.completed
          order by at,kind
          """;
        return jdbc.query(sql,p,(r,n)->new Event(r.getString(1),r.getTimestamp(2).toInstant(),eventLabel(r.getString(3))));
    }
    private static String eventLabel(String action){return switch(action){
        case "bill.create"->"创建消费账单";case "bill.collect"->"确认外部收款";case "bill.wallet_pay"->"顾客确认钱包付款";
        case "bill.scan_charge","bill.personal_scan_charge"->"扫码钱包付款";case "bill.bind_code"->"关联付款确认";case "bill.cancel"->"取消账单";
        case "topup.create","topup.customer_apply"->"创建充值申请";case "topup.confirm"->"确认充值入账";case "topup.cancel"->"取消充值";
        case "refund.create"->"发起退款";case "refund.confirm_part"->"确认外部退款";case "refund.cancel"->"取消退款";
        case "topup.exception_refunded"->"异常收款已退回";case "topup_credit"->"充值权益入账";case "bill_debit"->"钱包扣款";
        case "bill_refund"->"钱包退款回存";case "topup_refund"->"退回充值并移除权益";case "bonus_revoke"->"移除赠送权益";
        case "线下实收确认","外部退款完成"->action;default->"交易操作记录";
    };}
    private RowMapper<Item> mapper(boolean customer) {return (r,n)->new Item(r.getObject("id",UUID.class),r.getString("source_type"),r.getString("type"),r.getString("status"),instant(r,"occurred_at"),instant(r,"created_at"),instant(r,"posted_at"),r.getObject("store_id",UUID.class),r.getString("store_name"),r.getString("time_zone"),r.getString("currency"),r.getLong("amount_cents"),r.getLong("quantity"),r.getString("direction"),r.getLong("wallet_delta"),r.getLong("principal"),r.getLong("bonus"),r.getLong("credit"),r.getLong("wallet_paid"),r.getLong("external_paid"),r.getString("refund_status"),r.getObject("customer_id",UUID.class),r.getString("customer_name"),customer?null:maskPhone(r.getString("customer_phone")),r.getString("summary"),customer?null:r.getString("actor_name"),r.getString("original_source_type"),r.getObject("original_id",UUID.class));}
    private static String maskPhone(String phone){return phone==null?null:phone.length()<=4?"****":"****"+phone.substring(phone.length()-4);}
    private static Instant instant(ResultSet r,String name)throws SQLException{var t=r.getTimestamp(name);return t==null?null:t.toInstant();}
}
