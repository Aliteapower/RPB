package com.rpb.reservation.cards.history;
import com.rpb.reservation.common.scope.StoreScope;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CardReportRepository {
 public record Money(long receivedCents,long refundedCents){}
 public record Usage(UUID planId,String nameZh,String nameEn,String unit,long stamps,long redeemed,long rewards,long reversals){}
 public record Expired(UUID planId,String nameZh,String nameEn,String unit,long unused){}
 public record Report(LocalDate from,LocalDate to,Money money,List<Usage> usage,List<Expired> expiredDaily){}
 private final JdbcTemplate jdbc;public CardReportRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Report query(StoreScope s,LocalDate from,LocalDate to,String timezone){var start=Timestamp.from(from.atStartOfDay(ZoneId.of(timezone)).toInstant());var end=Timestamp.from(to.plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant());
  var money=jdbc.query("select coalesce(sum(amount_cents) filter(where kind='payment'),0),coalesce(sum(amount_cents) filter(where kind='refund'),0) from card_payment_links where tenant_id=? and store_id=? and created_at>=? and created_at<?",(r,n)->new Money(r.getLong(1),r.getLong(2)),s.tenantId().value(),s.storeId().value(),start,end).getFirst();
  var usage=jdbc.query("select p.id,p.name_zh,p.name_en,p.snapshot->'product'->>'unit' unit,coalesce(sum(e.quantity) filter(where e.kind='stamp'),0) stamps,coalesce(sum(e.quantity) filter(where e.kind='redeem'),0) redeemed,coalesce(sum(e.quantity) filter(where e.kind='grant' and c.kind='reward'),0) rewards,coalesce(sum(e.quantity) filter(where e.kind='reverse'),0) reversals from card_entries e join card_plans p on p.tenant_id=e.tenant_id and p.id=e.plan_id join customer_cards c on c.tenant_id=e.tenant_id and c.id=e.card_id where e.tenant_id=? and e.store_id=? and e.created_at>=? and e.created_at<? group by p.id,p.name_zh,p.name_en,p.snapshot order by p.name_zh,p.id",(r,n)->new Usage(r.getObject("id",UUID.class),r.getString("name_zh"),r.getString("name_en"),r.getString("unit"),r.getLong("stamps"),r.getLong("redeemed"),r.getLong("rewards"),r.getLong("reversals")),s.tenantId().value(),s.storeId().value(),start,end);
  var expired=jdbc.query("select p.id,p.name_zh,p.name_en,p.snapshot->'product'->>'unit' unit,sum(greatest(c.daily_limit-coalesce(u.used,0),0)) unused from customer_cards c join card_plans p on p.tenant_id=c.tenant_id and p.id=c.plan_id cross join lateral generate_series(greatest(?::date,(c.starts_at at time zone c.timezone)::date),least(?::date,(least(now(),c.expires_at,case when c.status<>'active' then c.updated_at else c.expires_at end) at time zone c.timezone)::date-1),interval '1 day') d(day) left join card_daily_usage u on u.tenant_id=c.tenant_id and u.card_id=c.id and u.day=d.day::date where c.tenant_id=? and c.store_id=? and c.kind='period' and c.initial_total is null group by p.id,p.name_zh,p.name_en,p.snapshot order by p.id",(r,n)->new Expired(r.getObject("id",UUID.class),r.getString("name_zh"),r.getString("name_en"),r.getString("unit"),r.getLong("unused")),java.sql.Date.valueOf(from),java.sql.Date.valueOf(to),s.tenantId().value(),s.storeId().value());
  return new Report(from,to,money,usage,expired);
 }
}
