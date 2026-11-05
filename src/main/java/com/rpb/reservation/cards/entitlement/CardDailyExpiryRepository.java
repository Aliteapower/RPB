package com.rpb.reservation.cards.entitlement;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** System settlement of unused daily entitlements, serialized with redemption by the card row lock. */
@Repository
@Transactional
public class CardDailyExpiryRepository {
 private static final UUID SYSTEM_ACTOR=new UUID(0,0);
 private final JdbcTemplate jdbc;
 public CardDailyExpiryRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private record Card(UUID id,UUID customer,UUID plan,int remaining,int daily,String zone,Instant expires,LocalDate from,LocalDate next){}
 private static final String ELIGIBLE="kind in('count','period') and status='active' and remaining>0 and daily_limit is not null";
 private static final String DUE="daily_expiry_next_day<least((?::timestamptz at time zone timezone)::date,(expires_at at time zone timezone)::date)";

 public List<StoreScope> dueScopes(Instant now){
  return jdbc.query("select distinct tenant_id,store_id from customer_cards where "+ELIGIBLE+" and "+DUE+" order by tenant_id,store_id",(r,n)->new StoreScope(new TenantId(r.getObject(1,UUID.class)),r.getObject(2,UUID.class)),Timestamp.from(now));
 }
 public void settleScope(StoreScope s,UUID customer,Instant now){
  var args=new ArrayList<Object>(List.of(s.tenantId().value(),s.storeId().value(),Timestamp.from(now)));
  String filter="";if(customer!=null){filter=" and customer_id=?";args.add(customer);}
  var ids=jdbc.query("select id from customer_cards where tenant_id=? and store_id=? and "+ELIGIBLE+" and "+DUE+filter+" order by id",(r,n)->r.getObject(1,UUID.class),args.toArray());
  for(var id:ids)settleCard(s,id,now);
 }
 public void settleCard(StoreScope s,UUID id,Instant now){
  var rows=jdbc.query("select * from customer_cards where tenant_id=? and store_id=? and id=? and "+ELIGIBLE+" and "+DUE+" for update",(r,n)->new Card(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),r.getObject("plan_id",UUID.class),r.getInt("remaining"),r.getInt("daily_limit"),r.getString("timezone"),r.getTimestamp("expires_at").toInstant(),r.getDate("daily_expiry_from").toLocalDate(),r.getDate("daily_expiry_next_day").toLocalDate()),s.tenantId().value(),s.storeId().value(),id,Timestamp.from(now));
  if(rows.isEmpty())return;
  var c=rows.getFirst();var until=CardRules.day(now.isBefore(c.expires())?now:c.expires(),c.zone());int remaining=c.remaining();
  var usage=new HashMap<LocalDate,Integer>();jdbc.query("select day,used from card_daily_usage where tenant_id=? and card_id=? and day>=? and day<?",r->{usage.put(r.getDate(1).toLocalDate(),r.getInt(2));},s.tenantId().value(),id,java.sql.Date.valueOf(c.next()),java.sql.Date.valueOf(until));
  for(var day=c.next();day.isBefore(until)&&remaining>0;day=day.plusDays(1)){
   int expired=CardRules.expiredDaily(remaining,c.daily(),usage.getOrDefault(day,0));
   if(expired>0){record(s,c,day,expired,remaining,remaining-expired,"daily-expire:"+id+":"+day,now);remaining-=expired;}
  }
  jdbc.update("update customer_cards set remaining=?,daily_expiry_next_day=?,version=version+1,updated_at=? where tenant_id=? and store_id=? and id=?",remaining,java.sql.Date.valueOf(until),Timestamp.from(now),s.tenantId().value(),s.storeId().value(),id);
 }
 /** A reversed redemption from a closed day cannot bring its expired daily allowance into today. */
 public void expireRestoredUse(StoreScope s,UUID id,LocalDate day,int restored,UUID original,Instant now){
  var rows=jdbc.query("select * from customer_cards where tenant_id=? and store_id=? and id=? and "+ELIGIBLE+" and daily_expiry_from<=? for update",(r,n)->new Card(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),r.getObject("plan_id",UUID.class),r.getInt("remaining"),r.getInt("daily_limit"),r.getString("timezone"),r.getTimestamp("expires_at").toInstant(),r.getDate("daily_expiry_from").toLocalDate(),r.getDate("daily_expiry_next_day").toLocalDate()),s.tenantId().value(),s.storeId().value(),id,java.sql.Date.valueOf(day));
  if(rows.isEmpty())return;var c=rows.getFirst();if(!day.isBefore(CardRules.day(now,c.zone())))return;if(!day.isBefore(c.next())){settleCard(s,id,now);return;}int qty=Math.min(restored,c.remaining());if(qty<1)return;
  record(s,c,day,qty,c.remaining(),c.remaining()-qty,"daily-expire-reversal:"+original,now);
  jdbc.update("update customer_cards set remaining=remaining-?,version=version+1,updated_at=? where tenant_id=? and store_id=? and id=?",qty,Timestamp.from(now),s.tenantId().value(),s.storeId().value(),id);
 }
 private void record(StoreScope s,Card c,LocalDate day,int qty,int before,int after,String source,Instant now){
  jdbc.update("insert into card_entries(id,tenant_id,store_id,card_id,customer_id,plan_id,kind,quantity,before_total,after_total,business_day,before_daily,after_daily,source,actor_id,reason,created_at) values(?,?,?,?,?,?,'daily_expire',?,?,?,?,?,0,?,?,?,?)",UUID.randomUUID(),s.tenantId().value(),s.storeId().value(),c.id(),c.customer(),c.plan(),qty,before,after,java.sql.Date.valueOf(day),qty,source,SYSTEM_ACTOR,"当日未用过期（系统结算，非消费）",Timestamp.from(now));
  jdbc.update("insert into card_audit(tenant_id,store_id,actor_id,operation,details,created_at) values(?,?,?,'daily.expire',jsonb_build_object('card',?::text,'businessDay',?::text,'quantity',?::int,'beforeTotal',?::int,'afterTotal',?::int,'source',?::text),?)",s.tenantId().value(),s.storeId().value(),SYSTEM_ACTOR,c.id().toString(),day.toString(),qty,before,after,source,Timestamp.from(now));
 }
}
