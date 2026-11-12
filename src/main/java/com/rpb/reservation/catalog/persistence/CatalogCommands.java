package com.rpb.reservation.catalog.persistence;
import com.fasterxml.jackson.databind.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.catalog.domain.CatalogException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CatalogCommands {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public CatalogCommands(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public <T>T run(StoreScope s,UUID actor,String op,String key,Object request,Class<T> type,Supplier<T> body){
  if(key==null||key.isBlank()||key.length()>100)throw CatalogException.invalid();
  try{String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(Map.of("store",s.storeId().value(),"request",request))));
   jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","catalog:"+s.tenantId().value()+actor+op+key);
   var old=jdbc.queryForList("select request_hash,response::text from catalog_commands where tenant_id=? and actor_id=? and operation=? and key=?",s.tenantId().value(),actor,op,key);
   if(!old.isEmpty()){if(!hash.equals(old.getFirst().get("request_hash")))throw CatalogException.stale();return json.readValue(old.getFirst().get("response").toString(),type);}
   T result=body.get();jdbc.update("insert into catalog_commands(tenant_id,actor_id,operation,key,request_hash,response) values(?,?,?,?,?,?::jsonb)",s.tenantId().value(),actor,op,key,hash,json.writeValueAsString(result));
   UUID source=result instanceof com.rpb.reservation.catalog.application.CatalogDtos.Product p?p.id():result instanceof com.rpb.reservation.catalog.application.CatalogDtos.Category c?c.id():s.storeId().value();
   jdbc.update("insert into catalog_audit(tenant_id,store_id,actor_id,action,source_id) values(?,?,?,?,?)",s.tenantId().value(),s.storeId().value(),actor,op,source);return result;
  }catch(com.fasterxml.jackson.core.JsonProcessingException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
