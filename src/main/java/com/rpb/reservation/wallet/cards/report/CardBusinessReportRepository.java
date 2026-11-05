package com.rpb.reservation.wallet.cards.report;

import com.rpb.reservation.common.scope.StoreScope;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.cards.report.CardBusinessReport.*;

/** Wallet reporting read model joining card snapshots and the scoped customer directory. */
@Repository
public class CardBusinessReportRepository {
 private final NamedParameterJdbcTemplate jdbc;
 public CardBusinessReportRepository(JdbcTemplate jdbc){this.jdbc=new NamedParameterJdbcTemplate(jdbc);}
 private static final String FILTERED="""
  with ledger as (
   select e.id::text id,e.card_id,e.customer_id,e.plan_id,e.kind,case when e.kind in('grant','refund') and c.kind<>'reward' then 1 else e.quantity end quantity,e.quantity ledger_quantity,e.before_total,e.after_total,
    e.before_daily,e.after_daily,e.business_day,e.created_at,e.source,e.original_id,e.reason,
    c.snapshot,c.status,c.starts_at,c.expires_at,c.kind card_kind,
    exists(select 1 from card_entries x where x.tenant_id=e.tenant_id and x.store_id=e.store_id and x.original_id=e.id) reversed
   from card_entries e join customer_cards c on c.tenant_id=e.tenant_id and c.store_id=e.store_id and c.id=e.card_id
   where e.tenant_id=:tenant and e.store_id=:store and
    ((:section='business' and e.kind<>'daily_expire' and e.created_at>=:start and e.created_at<:end)
     or (:section='expiry' and e.kind='daily_expire' and e.business_day between :from and :to))
   union all
   select 'expiry:'||c.id||':'||d.day::date,c.id,c.customer_id,c.plan_id,'daily_expire',
    greatest(c.daily_limit-coalesce(u.used,0),0)::integer,greatest(c.daily_limit-coalesce(u.used,0),0)::integer,null::integer,null::integer,
    greatest(c.daily_limit-coalesce(u.used,0),0)::integer,0,d.day::date,null::timestamptz,
    'calculated-daily-allowance',null::uuid,'',c.snapshot,c.status,c.starts_at,c.expires_at,c.kind,false
   from customer_cards c
   cross join lateral generate_series(greatest(:from::date,(c.starts_at at time zone c.timezone)::date),
    least(:to::date,(least(:now::timestamptz,c.expires_at,case when c.status<>'active' then c.updated_at else c.expires_at end) at time zone c.timezone)::date-1),interval '1 day') d(day)
   left join card_daily_usage u on u.tenant_id=c.tenant_id and u.card_id=c.id and u.day=d.day::date
   where :section='expiry' and c.tenant_id=:tenant and c.store_id=:store and c.kind='period' and c.initial_total is null
    and greatest(c.daily_limit-coalesce(u.used,0),0)>0
  ), filtered as (
   select l.*,p.display_name customer_name,p.phone_e164 customer_phone,
    l.snapshot->>'nameZh' name_zh,l.snapshot->>'nameEn' name_en,
    l.snapshot->'product'->>'nameZh' product_zh,l.snapshot->'product'->>'nameEn' product_en,
    case when l.kind in ('stamp','stamp_reverse') then 'stamp'
     when l.kind in('grant','refund') and l.card_kind<>'reward' then 'card'
     else coalesce(l.snapshot->'product'->>'unit','') end unit
   from ledger l left join customers p on p.tenant_id=:tenant and p.id=l.customer_id and p.status='active'
   where (cast(:planId as uuid) is null or l.plan_id=:planId)
    and (:kind='all' or l.kind=:kind or (:kind='reversal' and l.kind in ('reverse','stamp_reverse','reward_revoke')))
    and (:query='' or coalesce(p.display_name,'') ilike :pattern escape '\\' or coalesce(p.phone_e164,'') ilike :pattern escape '\\')
  )
  """;
 public Page query(StoreScope scope,Filter f,String timezone,Instant now){
  var zone=ZoneId.of(timezone);
  var p=new MapSqlParameterSource().addValue("tenant",scope.tenantId().value()).addValue("store",scope.storeId().value())
   .addValue("from",java.sql.Date.valueOf(f.from())).addValue("to",java.sql.Date.valueOf(f.to())).addValue("section",f.section()).addValue("kind",f.kind())
   .addValue("query",f.query()).addValue("pattern","%"+f.query().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%")
   .addValue("planId",f.planId()).addValue("start",Timestamp.from(f.from().atStartOfDay(zone).toInstant()))
   .addValue("end",Timestamp.from(f.to().plusDays(1).atStartOfDay(zone).toInstant())).addValue("now",Timestamp.from(now))
   .addValue("size",f.size()).addValue("offset",(long)f.page()*f.size()).addValue("groupBy",f.groupBy());
  long total=Objects.requireNonNull(jdbc.queryForObject(FILTERED+" select count(*) from filtered",p,Long.class));
  var items=jdbc.query(FILTERED+" select * from filtered order by case when :section='expiry' then business_day end desc,created_at desc nulls last,business_day desc,id desc limit :size offset :offset",p,(r,n)->row(r,now));
  var summary=jdbc.query(FILTERED+"""
   select case when :groupBy='plan' then plan_id end plan_id,
    case when :groupBy='plan' then name_zh else coalesce(customer_name,'') end name_zh,
    case when :groupBy='plan' then name_en else coalesce(customer_name,'') end name_en,unit,
    case when :groupBy='plan' then plan_id::text else customer_id::text end group_id,
    case when :groupBy='customer' then customer_phone end group_phone,count(*) count,
    coalesce(sum(quantity) filter(where kind='redeem'),0) redeemed,
    coalesce(sum(quantity) filter(where kind='reverse'),0) redeem_reversed,
    coalesce(sum(quantity) filter(where kind='stamp'),0) stamps,
    coalesce(sum(quantity) filter(where kind='stamp_reverse'),0) stamp_reversed,
    coalesce(sum(quantity) filter(where kind='grant'),0) granted,
    coalesce(sum(quantity) filter(where kind='reward_revoke'),0) revoked,
    coalesce(sum(quantity) filter(where kind='refund'),0) returned,
    coalesce(sum(quantity) filter(where kind='daily_expire'),0) expired
   from filtered group by 1,2,3,4,5,6 order by name_zh,group_id,unit
   """,p,(r,n)->new Summary(r.getObject("plan_id",UUID.class),r.getString("name_zh"),r.getString("name_en"),r.getString("unit"),r.getLong("count"),r.getLong("redeemed"),r.getLong("redeem_reversed"),r.getLong("stamps"),r.getLong("stamp_reversed"),r.getLong("granted"),r.getLong("revoked"),r.getLong("expired"),r.getLong("returned"),r.getString("group_id"),r.getString("group_phone")));
  Money money=null;
  if(f.section().equals("business")&&f.kind().equals("all"))money=jdbc.query("""
   select coalesce(sum(l.amount_cents) filter(where l.kind='payment'),0) received,
    coalesce(sum(l.amount_cents) filter(where l.kind='refund'),0) refunded
   from card_payment_links l join card_purchase_orders c on c.tenant_id=l.tenant_id and c.store_id=l.store_id and c.id=l.purchase_id
   left join customers p on p.tenant_id=c.tenant_id and p.id=c.customer_id and p.status='active'
   where l.tenant_id=:tenant and l.store_id=:store and l.created_at>=:start and l.created_at<:end
    and (cast(:planId as uuid) is null or c.plan_id=:planId)
    and (:query='' or coalesce(p.display_name,'') ilike :pattern escape '\\' or coalesce(p.phone_e164,'') ilike :pattern escape '\\')
   """,p,(r,n)->new Money(r.getLong("received"),r.getLong("refunded"))).getFirst();
  return new Page(timezone,f.page(),f.size(),total,items,summary,money);
 }
 private Row row(ResultSet r,Instant now)throws SQLException{
  var created=r.getTimestamp("created_at");String id=r.getString("id"),kind=r.getString("kind");
  boolean reversed=r.getBoolean("reversed"),reversible=!reversed&&("stamp".equals(kind)||"redeem".equals(kind)&&"active".equals(r.getString("status")));
  return new Row(id,r.getObject("card_id",UUID.class),r.getObject("customer_id",UUID.class),r.getString("customer_name"),r.getString("customer_phone"),r.getObject("plan_id",UUID.class),r.getString("name_zh"),r.getString("name_en"),r.getString("product_zh"),r.getString("product_en"),kind,r.getString("unit"),r.getInt("quantity"),r.getInt("ledger_quantity"),(Integer)r.getObject("before_total"),(Integer)r.getObject("after_total"),r.getInt("before_daily"),r.getInt("after_daily"),r.getDate("business_day").toLocalDate(),created==null?null:created.toInstant(),r.getString("source"),r.getObject("original_id",UUID.class),r.getString("reason"),reversed,reversible);
 }
}
