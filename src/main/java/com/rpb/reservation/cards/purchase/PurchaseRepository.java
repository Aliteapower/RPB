package com.rpb.reservation.cards.purchase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.cards.application.*;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.common.scope.StoreScope;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PurchaseRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public PurchaseRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public Purchase get(StoreScope s,UUID id,boolean lock){return jdbc.query("select * from card_purchase_orders where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),this::map,s.tenantId().value(),s.storeId().value(),id).stream().findFirst().orElseThrow(CardException::missing);}
 public List<Purchase> list(StoreScope s,UUID customer){return list(s,customer,null);}
 public List<Purchase> list(StoreScope s,UUID customer,UUID before){var args=new ArrayList<Object>(List.of(s.tenantId().value(),s.storeId().value()));String where="";if(customer!=null){where+=" and customer_id=?";args.add(customer);}if(before!=null){where+=" and (created_at,id)<(select created_at,id from card_purchase_orders where tenant_id=? and store_id=? and id=?)";args.add(s.tenantId().value());args.add(s.storeId().value());args.add(before);}return jdbc.query("select * from card_purchase_orders where tenant_id=? and store_id=?"+where+" order by created_at desc,id desc limit 500",this::map,args.toArray());}
 public Purchase create(StoreScope s,UUID customer,Plan p,long amount,Instant expires){UUID id=UUID.randomUUID();try{jdbc.update("insert into card_purchase_orders(id,tenant_id,store_id,customer_id,plan_id,amount_cents,snapshot,expires_at) values(?,?,?,?,?,?,?::jsonb,?)",id,s.tenantId().value(),s.storeId().value(),customer,p.id(),amount,json.writeValueAsString(p),Timestamp.from(expires));return get(s,id,false);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 public void issue(StoreScope s,UUID id,UUID card){jdbc.update("update card_purchase_orders set status='issued',card_id=?,updated_at=now() where tenant_id=? and store_id=? and id=?",card,s.tenantId().value(),s.storeId().value(),id);}
 public void refund(StoreScope s,UUID id){jdbc.update("update card_purchase_orders set status='refunded',updated_at=now() where tenant_id=? and store_id=? and id=?",s.tenantId().value(),s.storeId().value(),id);}
 public void exception(StoreScope s,UUID id,boolean refunded){jdbc.update("update card_purchase_orders set status=?,updated_at=now() where tenant_id=? and store_id=? and id=?",refunded?"exception_refunded":"exception",s.tenantId().value(),s.storeId().value(),id);}
 public void payment(StoreScope s,UUID id,PaymentReceipt p,String kind,UUID actor){jdbc.update("insert into card_payment_links(id,tenant_id,store_id,purchase_id,channel,account,receipt,amount_cents,kind,actor_id) values(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),s.tenantId().value(),s.storeId().value(),id,p.channel(),p.account(),p.receipt(),p.amountCents(),kind,actor);}
 public PaymentReceipt receipt(StoreScope s,UUID id){return jdbc.query("select * from card_payment_links where tenant_id=? and store_id=? and purchase_id=? and kind='payment'",(r,n)->new PaymentReceipt(r.getString("channel"),r.getString("account"),r.getString("receipt"),r.getLong("amount_cents")),s.tenantId().value(),s.storeId().value(),id).stream().findFirst().orElseThrow(CardException::missing);}
 private Purchase map(ResultSet r,int n)throws SQLException{try{return new Purchase(r.getObject("id",UUID.class),r.getObject("customer_id",UUID.class),r.getObject("plan_id",UUID.class),r.getObject("card_id",UUID.class),r.getLong("amount_cents"),r.getString("status"),r.getTimestamp("expires_at").toInstant(),json.readValue(r.getString("snapshot"),Plan.class));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
}
