package com.rpb.reservation.loyalty.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.loyalty.application.PointsViews.*;
import com.rpb.reservation.loyalty.domain.*;
import java.util.*;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PointsCommandRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public PointsCommandRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public Result run(Binding b,UUID actor,String operation,String key,Object request,Supplier<Result> work){
  PointsRules.required(key,100);if(actor==null)throw PointsException.invalid();
  try {
   String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(Map.of("storeId",b.storeId(),"programId",b.programId(),"request",request)).getBytes(StandardCharsets.UTF_8)));
   jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",b.tenantId()+":"+actor+":"+operation+":"+key);
   var rows=jdbc.queryForList("select request_hash,response::text from loyalty_commands where tenant_id=? and actor_id=? and operation=? and idempotency_key=?",b.tenantId(),actor,operation,key);
   if(!rows.isEmpty()){
    if(!digest.equals(rows.getFirst().get("request_hash")))throw PointsException.conflict("POINTS_IDEMPOTENCY_CONFLICT","同一请求编号不能用于不同内容");
    return json.readValue((String)rows.getFirst().get("response"),Result.class);
   }
   var result=work.get();String payload=json.writeValueAsString(result);
   jdbc.update("insert into loyalty_commands(tenant_id,store_id,actor_id,operation,idempotency_key,request_hash,response) values(?,?,?,?,?,?,?::jsonb)",b.tenantId(),b.storeId(),actor,operation,key,digest,payload);
   jdbc.update("insert into loyalty_audit(tenant_id,store_id,actor_id,action,source_id,details) values(?,?,?,?,?,?::jsonb)",b.tenantId(),b.storeId(),actor,operation,result.entry().id(),payload);
   return result;
  }catch(com.fasterxml.jackson.core.JsonProcessingException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
