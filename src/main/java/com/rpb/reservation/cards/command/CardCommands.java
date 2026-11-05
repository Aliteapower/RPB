package com.rpb.reservation.cards.command;
import com.fasterxml.jackson.databind.*;
import com.rpb.reservation.cards.application.CardException;
import com.rpb.reservation.common.scope.StoreScope;
import java.util.*;
import java.security.MessageDigest;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CardCommands {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public CardCommands(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 private void lock(StoreScope s,UUID who,String operation,String key){if(who==null||key==null||key.isBlank()||key.length()>100)throw CardException.invalid();jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","cards:"+s.tenantId().value()+":"+who+":"+operation+":"+key);}
 public <T>T run(StoreScope s,UUID who,String op,String key,Object request,Class<T> type,Supplier<T> body){lock(s,who,op,key);try{
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(Map.of("store",s.storeId().value(),"request",request))));
  var rows=jdbc.queryForList("select request_hash,response::text,store_id from card_commands where tenant_id=? and principal_id=? and operation=? and key=?",s.tenantId().value(),who,op,key);
  if(!rows.isEmpty()){var old=rows.getFirst();if(!s.storeId().value().equals(old.get("store_id")))throw CardException.missing();var response=json.readTree(old.get("response").toString());if(response.path("closed").asBoolean())throw new CardException("CARD_REQUEST_CLOSED",409,"原请求已关闭，请重新操作");if(!hash.equals(old.get("request_hash")))throw new CardException("CARD_IDEMPOTENCY_CONFLICT",409,"同一编号不能用于不同内容");return json.treeToValue(response,type);}
  T value=body.get();jdbc.update("insert into card_commands(tenant_id,store_id,principal_id,operation,key,request_hash,response) values(?,?,?,?,?,?,?::jsonb)",s.tenantId().value(),s.storeId().value(),who,op,key,hash,json.writeValueAsString(value));audit(s,who,op,request);return value;
 }catch(com.fasterxml.jackson.core.JsonProcessingException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public JsonNode result(StoreScope s,UUID who,String op,String key){return jdbc.query("select response::text from card_commands where tenant_id=? and store_id=? and principal_id=? and operation=? and key=?",(r,n)->decode(r.getString(1)),s.tenantId().value(),s.storeId().value(),who,op,key).stream().findFirst().orElseThrow(CardException::missing);}
 public JsonNode reconcile(StoreScope s,UUID who,String op,String key){lock(s,who,op,key);var existing=jdbc.queryForList("select store_id from card_commands where tenant_id=? and principal_id=? and operation=? and key=?",s.tenantId().value(),who,op,key);if(!existing.isEmpty())return result(s,who,op,key);jdbc.update("insert into card_commands(tenant_id,store_id,principal_id,operation,key,request_hash,response) values(?,?,?,?,?,'closed','{\"closed\":true}'::jsonb)",s.tenantId().value(),s.storeId().value(),who,op,key);return decode("{\"closed\":true}");}
 public void audit(StoreScope s,UUID who,String op,Object request){jdbc.update("insert into card_audit(tenant_id,store_id,actor_id,operation,details) values(?,?,?,?,?::jsonb)",s.tenantId().value(),s.storeId().value(),who,op,encode(request));}
 public String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 private JsonNode decode(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
}
