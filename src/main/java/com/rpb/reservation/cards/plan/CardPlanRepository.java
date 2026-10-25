package com.rpb.reservation.cards.plan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.common.scope.StoreScope;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CardPlanRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public CardPlanRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public Plan get(StoreScope s,UUID id,boolean lock){return jdbc.query("select snapshot::text,status,version from card_plans where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),(r,n)->{Plan p=decode(r.getString(1));return new Plan(p.id(),p.storeId(),p.nameZh(),p.nameEn(),p.kind(),p.productId(),p.total(),p.dailyLimit(),p.validDays(),p.stampTarget(),p.repeatable(),p.rewardDays(),p.priceCents(),p.taxMode(),p.taxRate(),p.product(),r.getString(2),r.getInt(3));},s.tenantId().value(),s.storeId().value(),id).stream().findFirst().orElseThrow(CardException::missing);}
 public List<Plan> list(StoreScope s){return jdbc.query("select id from card_plans where tenant_id=? and store_id=? order by created_at desc limit 300",(r,n)->get(s,r.getObject(1,UUID.class),false),s.tenantId().value(),s.storeId().value());}
 public void create(StoreScope s,UUID actor,UUID issuer,Plan p){jdbc.update("insert into card_plans(id,tenant_id,store_id,issuer_id,product_id,kind,name_zh,name_en,total,daily_limit,valid_days,stamp_target,repeatable,reward_days,price_cents,tax_mode,tax_rate,snapshot,created_by) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?)",p.id(),s.tenantId().value(),s.storeId().value(),issuer,p.productId(),p.kind(),p.nameZh(),p.nameEn(),p.total(),p.dailyLimit(),p.validDays(),p.stampTarget(),p.repeatable(),p.rewardDays(),p.priceCents(),p.taxMode(),p.taxRate(),encode(p),actor);}
 public void status(StoreScope s,UUID id,PlanStatus c){if(jdbc.update("update card_plans set status=?,version=version+1,updated_at=now() where tenant_id=? and store_id=? and id=? and version=?",c.status(),s.tenantId().value(),s.storeId().value(),id,c.expectedVersion())!=1)throw new CardException("CARD_STALE",409,"卡方案已变化");}
 public Plan decode(String data){try{return json.readValue(data,Plan.class);}catch(Exception e){throw new IllegalStateException(e);}}
 public String encode(Object p){try{return json.writeValueAsString(p);}catch(Exception e){throw new IllegalStateException(e);}}
}
