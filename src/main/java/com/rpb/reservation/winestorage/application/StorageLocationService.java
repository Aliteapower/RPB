package com.rpb.reservation.winestorage.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.winestorage.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

/** Store-owned receipt locations. Names are resolved only inside the receipt transaction. */
@Service
@Transactional(readOnly=true)
public class StorageLocationService {
 private final StorageAccess access;private final StorageRepository r;private final StorageCommands commands;
 public StorageLocationService(StorageAccess access,StorageRepository r,StorageCommands commands){this.access=access;this.r=r;this.commands=commands;}
 private UUID defaultId(StoreScope s){return UUID.nameUUIDFromBytes(("wine-storage-location:"+s.tenantId().value()+":"+s.storeId().value()).getBytes(StandardCharsets.UTF_8));}
 private List<StorageLocation> catalog(StoreScope s){
  var rows=new ArrayList<>(r.rows(s,"select id,name,is_default,version from wine_storage_locations where tenant_id=? and store_id=? order by is_default desc,lower(name),id").stream().map(row->new StorageLocation((UUID)row.get("id"),(String)row.get("name"),(Boolean)row.get("is_default"),((Number)row.get("version")).intValue())).toList());
  if(rows.stream().noneMatch(StorageLocation::defaultLocation))rows.addFirst(new StorageLocation(defaultId(s),"前台寄存处",true,0));
  return rows;
 }
 public List<StorageLocation> list(StoreScope s,CurrentActor actor){access.staff(s,actor,"wine_storage.view",false);return catalog(s);}
 public LocationRef resolve(StoreScope s,UUID id){
  if(id==null)return null;
  r.lock(s,"locations");var found=catalog(s).stream().filter(row->row.id().equals(id)).findFirst().orElseThrow(StorageException::missing);
  return new LocationRef(found.id(),found.name());
 }
 @Transactional public StorageLocation save(StoreScope s,CurrentActor actor,UUID id,LocationWrite c){
  access.staff(s,actor,"wine_storage.rules.manage",true);
  if(!actor.roles().contains("tenant_admin"))throw StorageException.forbidden();
  if(c==null||c.name()==null||c.name().trim().isEmpty()||c.name().trim().length()>100||id==null&&c.version()!=null||id!=null&&c.version()==null)throw StorageException.invalid();
  return commands.run(s,actor.actorId(),"staff","location.write",c.idempotencyKey(),Map.of("id",id==null?"new":id.toString(),"command",c),StorageLocation.class,()->{
   r.lock(s,"locations");var all=catalog(s);String name=c.name().trim();
   StorageLocation old=id==null?null:all.stream().filter(row->row.id().equals(id)).findFirst().orElseThrow(StorageException::missing);
   if(all.stream().anyMatch(row->!row.id().equals(id)&&row.name().equalsIgnoreCase(name)))throw StorageException.conflict("存放位置名称已存在");
   if(old!=null&&old.version()!=c.version())throw StorageException.conflict("存放位置已更新，请刷新后重试");
   UUID key=id==null?UUID.randomUUID():id;var result=new StorageLocation(key,name,key.equals(defaultId(s)),old==null?0:old.version()+1);
   boolean exists=id!=null&&!r.rows(s,"select id from wine_storage_locations where tenant_id=? and store_id=? and id=?",id).isEmpty();
   if(!exists)r.update(s,"insert into wine_storage_locations(tenant_id,store_id,id,name,is_default,version,created_by,updated_by) values(?,?,?,?,?,?,?,?)",key,name,result.defaultLocation(),result.version(),actor.actorId(),actor.actorId());
   else r.update(s,"with scope(tenant,store) as (values(?::uuid,?::uuid)) update wine_storage_locations set name=?,version=?,updated_by=?,updated_at=now() from scope where tenant_id=scope.tenant and store_id=scope.store and id=?",name,result.version(),actor.actorId(),key);
   r.update(s,"insert into wine_storage_location_events(tenant_id,store_id,id,location_id,actor_id,details) values(?,?,?,?,?,?::jsonb)",UUID.randomUUID(),key,actor.actorId(),r.json(result));return result;
  });
 }
}
