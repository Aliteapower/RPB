package com.rpb.reservation.winestorage.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.winestorage.application.StorageDtos.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StorageRepository {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public StorageRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public String json(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 public String canonical(Object value){try{return json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 public <T>T read(String value,Class<T> type){try{return value==null?null:json.readValue(value,type);}catch(Exception e){throw new IllegalStateException(e);}}
 public Map<String,Object> details(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
 public void update(StoreScope s,String sql,Object...args){jdbc.update(sql,scopeArgs(s,args));}
 public void change(StoreScope s,String table,UUID id,String set,Object...args){
  if(!Set.of("applications","suggestions","intents","items","redeposits").contains(table))throw new IllegalArgumentException("table");
  Object[] all=Arrays.copyOf(args,args.length+3);all[args.length]=s.tenantId().value();all[args.length+1]=s.storeId().value();all[args.length+2]=id;
  jdbc.update("update wine_storage_"+table+" set "+set+" where tenant_id=? and store_id=? and "+(table.equals("suggestions")?"line_id":table.equals("redeposits")?"application_id":"id")+"=?",all);
 }
 public List<Map<String,Object>> rows(StoreScope s,String sql,Object...args){return jdbc.queryForList(sql,scopeArgs(s,args));}
 private Object[] scopeArgs(StoreScope s,Object[] args){Object[] all=new Object[args.length+2];all[0]=s.tenantId().value();all[1]=s.storeId().value();System.arraycopy(args,0,all,2,args.length);return all;}
 public static UUID id(ResultSet r,String key)throws SQLException{return r.getObject(key,UUID.class);}
 public static Instant time(ResultSet r,String key)throws SQLException{Timestamp t=r.getTimestamp(key);return t==null?null:t.toInstant();}
 public static Timestamp at(Instant time){return time==null?null:Timestamp.from(time);}
 public void lock(StoreScope s,String key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","wine:"+s.tenantId().value()+":"+s.storeId().value()+":"+key);}
 public Settings settings(StoreScope s){
  var rows=rows(s,"select operation_mode,version from wine_storage_settings where tenant_id=? and store_id=?");
  return rows.isEmpty()?new Settings("customer_verification",0):new Settings((String)rows.getFirst().get("operation_mode"),((Number)rows.getFirst().get("version")).intValue());
 }
 public void requireCustomerMode(StoreScope s){lock(s,"settings");if(!settings(s).operationMode().equals("customer_verification"))throw StorageException.conflict("本店由商户直接登记存取酒，请联系门店办理");}
 public List<Rule> rules(StoreScope s){return jdbc.query("select * from wine_storage_rules where tenant_id=? and store_id=? order by version desc",(r,n)->rule(r),scopeArgs(s,new Object[]{}));}
 private Rule rule(ResultSet r)throws SQLException{return new Rule(id(r,"id"),id(r,"category_id"),r.getString("name"),r.getInt("months"),r.getInt("version"));}
 public Rule rule(StoreScope s,UUID id){return rules(s).stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(StorageException::missing);}
 public void addRule(StoreScope s,Rule r,UUID actor){update(s,"insert into wine_storage_rules(tenant_id,store_id,id,category_id,name,months,version,created_by) values(?,?,?,?,?,?,?,?)",r.id(),r.categoryId(),r.name(),r.months(),r.version(),actor);}
 public List<Application> applications(StoreScope s,UUID customer){return jdbc.query("select * from wine_storage_applications where tenant_id=? and store_id=?"+(customer==null?"":" and customer_id=?")+" order by created_at desc",(r,n)->application(s,r),scopeArgs(s,customer==null?new Object[]{}:new Object[]{customer}));}
 private Application application(StoreScope s,ResultSet r)throws SQLException {
  UUID app=id(r,"id");var lines=jdbc.query("select original::text from wine_storage_application_lines where tenant_id=? and store_id=? and application_id=? order by id",(row,n)->read(row.getString(1),Line.class),scopeArgs(s,new Object[]{app}));
  return new Application(app,id(r,"customer_id"),r.getString("status"),read(r.getString("original"),Apply.class),lines,read(r.getString("confirmation"),Receive.class),r.getString("reason"),time(r,"created_at"));
 }
 public Application application(StoreScope s,UUID id,boolean lock){return jdbc.query("select * from wine_storage_applications where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),(r,n)->application(s,r),scopeArgs(s,new Object[]{id})).stream().findFirst().orElseThrow(StorageException::missing);}
 public List<Item> items(StoreScope s,UUID customer,Instant now){return jdbc.query("select * from wine_storage_items where tenant_id=? and store_id=?"+(customer==null?"":" and customer_id=?")+" order by received_at desc",(r,n)->item(r,now),scopeArgs(s,customer==null?new Object[]{}:new Object[]{customer}));}
 public Item item(StoreScope s,UUID id,boolean lock,Instant now){return jdbc.query("select * from wine_storage_items where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),(r,n)->item(r,now),scopeArgs(s,new Object[]{id})).stream().findFirst().orElseThrow(StorageException::missing);}
 private Item item(ResultSet r,Instant now)throws SQLException {
  int left=r.getInt("remaining");Instant expiry=time(r,"expires_at");return new Item(id(r,"id"),id(r,"application_id"),id(r,"customer_id"),read(r.getString("snapshot"),Snapshot.class),r.getInt("quantity"),left,r.getBoolean("opened"),r.getString("source"),r.getString("location"),r.getString("label"),time(r,"received_at"),expiry,left==0?"collected":!now.isBefore(expiry)?"expired":"stored");
 }
 public void addItem(StoreScope s,UUID app,UUID line,UUID rule,Item i){update(s,"insert into wine_storage_items(tenant_id,store_id,id,customer_id,application_id,line_id,product_id,rule_id,snapshot,quantity,remaining,opened,source,location,label,received_at,expires_at) values(?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?)",i.id(),i.customerId(),app,line,i.snapshot().productId(),rule,json(i.snapshot()),i.quantity(),i.remaining(),i.opened(),i.source(),i.location(),i.label(),at(i.receivedAt()),at(i.expiresAt()));}
 public List<Selection> selections(StoreScope s,UUID id){return jdbc.query("select item_id,quantity from wine_storage_intent_lines where tenant_id=? and store_id=? and intent_id=? order by item_id",(r,n)->new Selection(StorageRepository.id(r,"item_id"),r.getInt("quantity")),scopeArgs(s,new Object[]{id}));}
 public Intent intent(StoreScope s,UUID id,boolean lock){return jdbc.query("select * from wine_storage_intents where tenant_id=? and store_id=? and id=?"+(lock?" for update":""),(r,n)->intent(s,r),scopeArgs(s,new Object[]{id})).stream().findFirst().orElseThrow(StorageException::missing);}
 private Intent intent(StoreScope s,ResultSet r)throws SQLException{return new Intent(id(r,"id"),id(r,"customer_id"),r.getString("origin"),r.getString("status"),time(r,"expires_at"),time(r,"completed_at"),selections(s,id(r,"id")),null);}
 public List<Intent> intents(StoreScope s,UUID customer){return jdbc.query("select * from wine_storage_intents where tenant_id=? and store_id=?"+(customer==null?"":" and customer_id=?")+" order by created_at desc",(r,n)->intent(s,r),scopeArgs(s,customer==null?new Object[]{}:new Object[]{customer}));}
 public Intent byCode(StoreScope s,String hash){var rows=rows(s,"select id from wine_storage_intents where tenant_id=? and store_id=? and code_hash=?",hash);if(rows.isEmpty())throw StorageException.missing();return intent(s,(UUID)rows.getFirst().get("id"),false);}
 public Event event(StoreScope s,UUID customer,UUID source,String action,UUID actor,Object payload,Instant now){UUID event=UUID.randomUUID();String details=json(payload);update(s,"insert into wine_storage_events(tenant_id,store_id,id,customer_id,source_id,action,actor_id,details,created_at) values(?,?,?,?,?,?,?,?::jsonb,?)",event,customer,source,action,actor,details,at(now));return new Event(event,source,action,actor,details(details),now);}
 public List<Event> events(StoreScope s,UUID customer){return jdbc.query("select * from wine_storage_events where tenant_id=? and store_id=?"+(customer==null?"":" and customer_id=?")+" order by created_at desc",(r,n)->new Event(id(r,"id"),id(r,"source_id"),r.getString("action"),id(r,"actor_id"),details(r.getString("details")),time(r,"created_at")),scopeArgs(s,customer==null?new Object[]{}:new Object[]{customer}));}
}
