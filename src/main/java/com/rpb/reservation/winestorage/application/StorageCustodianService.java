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
@Transactional(readOnly=true)
public class StorageCustodianService {
 private final StorageAccess access;private final StorageRepository r;private final StorageCommands commands;
 public StorageCustodianService(StorageAccess access,StorageRepository r,StorageCommands commands){this.access=access;this.r=r;this.commands=commands;}
 private void manager(StoreScope s,CurrentActor actor){access.staff(s,actor,"wine_storage.custodians.manage",false);if(!actor.roles().contains("tenant_admin")&&!actor.roles().contains("store_manager"))throw StorageException.forbidden();}
 public List<Custodian> list(StoreScope s,CurrentActor actor,boolean includeDisabled){access.staff(s,actor,"wine_storage.view",false);if(includeDisabled)manager(s,actor);return r.rows(s,"select id,name,kind,enabled,version from wine_storage_custodians where tenant_id=? and store_id=?"+(includeDisabled?"":" and enabled")+" order by lower(name),id").stream().map(this::view).toList();}
 private Custodian view(Map<String,Object> row){return new Custodian((UUID)row.get("id"),(String)row.get("name"),(String)row.get("kind"),(Boolean)row.get("enabled"),((Number)row.get("version")).intValue());}
 // Called only from the enclosing receipt transaction. A shared lock serializes rename/disable with snapshotting.
 public CustodianRef resolve(StoreScope s,UUID id){if(id==null)return null;var row=r.rows(s,"select id,name,kind,enabled,version from wine_storage_custodians where tenant_id=? and store_id=? and id=? and enabled for share",id).stream().findFirst().orElseThrow(StorageException::missing);var person=view(row);return new CustodianRef(person.id(),person.name(),person.kind());}
 @Transactional public Custodian save(StoreScope s,CurrentActor actor,UUID id,CustodianWrite c){
  manager(s,actor);if(c==null||c.enabled()==null||c.name()==null||c.name().trim().isEmpty()||c.name().trim().length()>100||!Set.of("hostess","custodian").contains(Objects.toString(c.kind(),""))||id==null&&c.version()!=null||id!=null&&c.version()==null)throw StorageException.invalid();
  return commands.run(s,actor.actorId(),"staff","custodian.write",c.idempotencyKey(),Map.of("id",id==null?"new":id.toString(),"command",c),Custodian.class,()->{
   UUID key=id==null?UUID.randomUUID():id;int version=0;
   if(id!=null){var old=r.rows(s,"select id,name,kind,enabled,version from wine_storage_custodians where tenant_id=? and store_id=? and id=? for update",id).stream().findFirst().orElseThrow(StorageException::missing);version=view(old).version();if(version!=c.version())throw StorageException.conflict("人员资料已更新，请刷新后重试");version++;}
   var result=new Custodian(key,c.name().trim(),c.kind(),c.enabled(),version);
   if(id==null)r.update(s,"insert into wine_storage_custodians(tenant_id,store_id,id,name,kind,enabled,created_by,updated_by) values(?,?,?,?,?,?,?,?)",key,result.name(),result.kind(),result.enabled(),actor.actorId(),actor.actorId());
   else r.update(s,"with scope(tenant,store) as (values(?::uuid,?::uuid)) update wine_storage_custodians set name=?,kind=?,enabled=?,version=?,updated_by=?,updated_at=now() from scope where tenant_id=scope.tenant and store_id=scope.store and id=?",result.name(),result.kind(),result.enabled(),result.version(),actor.actorId(),key);
   r.update(s,"insert into wine_storage_custodian_events(tenant_id,store_id,id,custodian_id,actor_id,details) values(?,?,?,?,?,?::jsonb)",UUID.randomUUID(),key,actor.actorId(),r.json(result));return result;
  });
 }
}
