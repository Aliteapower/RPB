package com.rpb.reservation.winestorage.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Caller supplies the enclosing business transaction. Locks and response commit together. */
@Component
public class StorageCommands {
 private final StorageRepository r;
 public StorageCommands(StorageRepository r){this.r=r;}
 public static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public <T>T run(StoreScope s,UUID actor,String actorType,String operation,String key,Object request,Class<T> type,Supplier<T> action){
  if(key==null||key.isBlank()||key.length()>100)throw StorageException.invalid();
  String hash=digest(r.canonical(request));r.lock(s,actor+":"+actorType+":"+operation+":"+key);
  var old=r.rows(s,"select request_hash,response::text from wine_storage_commands where tenant_id=? and store_id=? and actor_id=? and actor_type=? and operation=? and key=?",actor,actorType,operation,key);
  if(!old.isEmpty()){if(!hash.equals(old.getFirst().get("request_hash")))throw StorageException.conflict("幂等键已用于不同内容");return r.read(old.getFirst().get("response").toString(),type);}
  T result=action.get();r.update(s,"insert into wine_storage_commands(tenant_id,store_id,actor_id,actor_type,operation,key,request_hash,response) values(?,?,?,?,?,?,?,?::jsonb)",actor,actorType,operation,key,hash,r.json(result));return result;
 }
}
