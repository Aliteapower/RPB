package com.rpb.reservation.wallet.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.wallet.domain.*;
import java.time.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.wallet.marketing.MarketingDtos.*;

/** Plan-scoped persistence. Callers hold the plan lock before changing/selecting live configuration. */
@Repository
public class MarketingRepository {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public MarketingRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 /** Access collaborators through target-dispatched methods; CGLIB proxy fields are not initialized. */
 public JdbcTemplate jdbc(){return jdbc;}
 public ObjectMapper json(){return json;}
 public void lock(UUID tenant,UUID program){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","wallet.marketing:"+tenant+":"+program);}
 String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 static Instant instant(Object v){return v==null?null:((Timestamp)v).toInstant();}
 static int integer(Map<String,Object> m,String k){return ((Number)m.get(k)).intValue();}
 static long number(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
 Map<String,Object> settings(UUID t,UUID p){return jdbc.queryForMap("select * from wallet_marketing_settings where tenant_id=? and program_id=?",t,p);}
 void initialize(UUID t,UUID p,UUID actor){
  int n=jdbc.update("insert into wallet_marketing_settings(tenant_id,program_id) values(?,?) on conflict do nothing",t,p);
  if(n==0)return;
  List<OfferInput> base=List.of(new OfferInput("100","0"),new OfferInput("300","0"),new OfferInput("500","0"),new OfferInput("1000","0"));
  createSet(t,p,null,1,base,actor);
  UUID id=UUID.randomUUID();jdbc.update("insert into wallet_campaigns(id,tenant_id,program_id,name,template_key) values(?,?,?,'充值赠送活动模板','initial_topup_bonus_v1')",id,t,p);
  jdbc.update("insert into wallet_campaign_revisions(tenant_id,program_id,campaign_id,revision,start_at,end_at,priority,created_by) values(?,?,?,1,now(),now()+interval '30 days',0,?)",t,p,id,actor);
  createSet(t,p,id,1,List.of(new OfferInput("100","10"),new OfferInput("300","50"),new OfferInput("500","80"),new OfferInput("1000","200")),actor);
 }
 void createSet(UUID t,UUID p,UUID campaign,int revision,List<OfferInput> offers,UUID actor){
  validateOffers(offers,campaign==null);
  UUID set=UUID.randomUUID();jdbc.update("insert into wallet_topup_offer_sets(id,tenant_id,program_id,kind,campaign_id,campaign_revision,revision,created_by) values(?,?,?,?,?,?,?,?)",set,t,p,campaign==null?"base":"campaign",campaign,campaign==null?null:revision,revision,actor);
  int i=0;for(var offer:offers)jdbc.update("insert into wallet_topup_offers(tenant_id,program_id,offer_set_id,principal_cents,bonus_cents,sort_order) values(?,?,?,?,?,?)",t,p,set,WalletMoney.cents(offer.amount()),bonus(offer.bonus()),i++);
 }
 static long bonus(String s){if(s==null||s.matches("0(?:\\.0{1,2})?"))return 0;return WalletMoney.cents(s);}
 public static void validateOffers(List<OfferInput> offers,boolean base){
  if(offers==null||offers.isEmpty()||offers.size()>50)throw WalletException.invalid();
  Set<Long> amounts=new HashSet<>();for(var o:offers){if(o==null)throw WalletException.invalid();long p=WalletMoney.cents(o.amount()),b=bonus(o.bonus());if(!amounts.add(p)||p+b>WalletMoney.MAX_CENTS||(base&&b!=0))throw WalletException.invalid();}
 }
 List<Offer> offers(UUID t,UUID p,UUID campaign,int revision){
  return jdbc.query("select o.*,s.revision from wallet_topup_offers o join wallet_topup_offer_sets s on s.id=o.offer_set_id and s.tenant_id=o.tenant_id and s.program_id=o.program_id where o.tenant_id=? and o.program_id=? and s.revision=? and "+(campaign==null?"s.kind='base'":"s.campaign_id=?")+" and o.enabled order by o.sort_order,o.id",(rs,n)->new Offer(rs.getObject("id",UUID.class),rs.getInt("revision"),rs.getLong("principal_cents"),rs.getLong("bonus_cents"),rs.getLong("principal_cents")+rs.getLong("bonus_cents")),campaign==null?new Object[]{t,p,revision}:new Object[]{t,p,revision,campaign});
 }
 int baseRevision(UUID t,UUID p){return jdbc.queryForObject("select max(revision) from wallet_topup_offer_sets where tenant_id=? and program_id=? and kind='base'",Integer.class,t,p);}
 Map<String,Object> active(UUID t,UUID p){
  var rows=jdbc.queryForList("select c.id,c.name,r.* from wallet_campaigns c join wallet_campaign_revisions r on r.campaign_id=c.id and r.tenant_id=c.tenant_id and r.program_id=c.program_id and r.revision=c.current_published_revision where c.tenant_id=? and c.program_id=? and c.status='published' and r.start_at<=clock_timestamp() and r.end_at>clock_timestamp() order by r.priority desc,c.id limit 1",t,p);
  return rows.isEmpty()?null:rows.getFirst();
 }
 Campaign campaign(UUID t,UUID p,UUID id){
  var rows=jdbc.queryForList("select * from wallet_campaigns where tenant_id=? and program_id=? and id=?",t,p,id);if(rows.isEmpty())throw WalletException.missing();var c=rows.getFirst();
  List<Revision> revisions=jdbc.query("select * from wallet_campaign_revisions where tenant_id=? and program_id=? and campaign_id=? order by revision desc",(rs,n)->new Revision(rs.getInt("revision"),rs.getTimestamp("start_at").toInstant(),rs.getTimestamp("end_at").toInstant(),rs.getInt("priority"),rs.getString("terms_snapshot"),offers(t,p,id,rs.getInt("revision"))),t,p,id);
  return new Campaign(id,(String)c.get("name"),(String)c.get("status"),integer(c,"version"),(Integer)c.get("current_published_revision"),revisions,instant(c.get("created_at")));
 }
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void rate(UUID t,UUID p,String subject,String operation,int limit){
  int count=jdbc.queryForObject("insert into wallet_marketing_rate_limits(tenant_id,program_id,subject,operation,window_at,counter) values(?,?,?,?,date_trunc('minute',clock_timestamp()),1) on conflict(tenant_id,program_id,subject,operation,window_at) do update set counter=wallet_marketing_rate_limits.counter+1 returning counter",Integer.class,t,p,subject,operation);
  if(count>limit)throw new WalletException("WALLET_REQUEST_RATE_LIMITED",429,"请求过于频繁，请稍后重试");
 }
}
