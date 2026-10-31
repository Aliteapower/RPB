package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

@Service
public class StorageSettingsService {
 private final StorageAccess access;private final StorageRepository r;private final StorageCommands commands;
 public StorageSettingsService(StorageAccess access,StorageRepository r,StorageCommands commands){this.access=access;this.r=r;this.commands=commands;}
 @Transactional public Settings save(StoreScope s,CurrentActor a,SettingsWrite c){
  access.staff(s,a,"wine_storage.rules.manage",true);
  if(!a.roles().contains("tenant_admin"))throw StorageException.forbidden();
  if(c==null||c.operationMode()==null||!Set.of("customer_verification","staff_registration").contains(c.operationMode())||c.version()<0)throw StorageException.invalid();
  return commands.run(s,a.actorId(),"staff","settings.write",c.idempotencyKey(),c,Settings.class,()->{
   r.lock(s,"settings");var old=r.settings(s);
   if(c.version()!=old.version())throw StorageException.conflict("存取酒模式已更新，请刷新后重试");
   var result=new Settings(c.operationMode(),old.version()+1);
   r.update(s,"insert into wine_storage_settings(tenant_id,store_id,operation_mode,version,updated_by) values(?,?,?,?,?) on conflict(tenant_id,store_id) do update set operation_mode=excluded.operation_mode,version=excluded.version,updated_by=excluded.updated_by,updated_at=now()",result.operationMode(),result.version(),a.actorId());
   r.update(s,"insert into wine_storage_settings_events(tenant_id,store_id,id,actor_id,details) values(?,?,?,?,?::jsonb)",UUID.randomUUID(),a.actorId(),r.json(Map.of("before",old,"after",result)));
   return result;
  });
 }
}
