package com.rpb.reservation.winestorage.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.winestorage.application.StorageInventoryDtos.*;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.rpb.reservation.winestorage.persistence.StorageRepository.*;

/** Inventory uses remaining physical bottles; expiry does not remove held stock. */
@Repository
public class StorageInventoryRepository {
 private final JdbcTemplate jdbc;
 public StorageInventoryRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Page list(StoreScope scope,String query,String status,int page,int size,Instant now){
  return read(scope,query,status,page,size,now,false);
 }
 public Page export(StoreScope scope,String query,String status,Instant now){return read(scope,query,status,0,50000,now,true);}
 private Page read(StoreScope scope,String query,String status,int page,int size,Instant now,boolean exporting){
  String from="""
   from wine_storage_items i
   left join customers c on c.id=i.customer_id and c.tenant_id=i.tenant_id
   where i.tenant_id=? and i.store_id=? and i.remaining>0 and i.physical_disposition='held'
   """;
  var args=new ArrayList<Object>();args.add(scope.tenantId().value());args.add(scope.storeId().value());
  if(!query.isEmpty()){
   from+=" and strpos(lower(concat_ws(' ',i.snapshot->>'nameZh',i.snapshot->>'nameEn',c.display_name,c.phone_e164,i.snapshot->'storageLocation'->>'name',i.location,i.label,i.snapshot->'custodian'->>'name')),lower(?))>0";args.add(query);
  }
  if(!status.equals("all")){from+=" and i.expires_at"+(status.equals("expired")?"<=?":">?");args.add(at(now));}
  var totals=new ArrayList<Object>();totals.add(at(now));totals.addAll(args);
  Summary summary=jdbc.queryForObject("select count(distinct i.customer_id),coalesce(sum(i.remaining),0),coalesce(sum(i.remaining) filter(where i.opened),0),coalesce(sum(i.remaining) filter(where i.expires_at<=?),0),count(*) "+from,(r,n)->new Summary(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4),r.getLong(5)),totals.toArray());
  if(exporting&&summary.itemCount()>50000)throw new com.rpb.reservation.winestorage.domain.StorageException("STORAGE_EXPORT_TOO_LARGE",422,"导出超过 50,000 条，请缩小筛选范围 / Over 50,000 records; narrow the filters");
  args.add(size);args.add((long)page*size);
  var items=jdbc.query("select i.*,c.display_name as customer_name,c.phone_e164 as customer_phone,i.snapshot->>'nameZh' as name_zh,i.snapshot->>'nameEn' as name_en,i.snapshot->'storageLocation'->>'name' as location_name,i.snapshot->'custodian'->>'name' as custodian_name "+from+" order by i.received_at desc,i.id desc limit ? offset ?",(r,n)->new Row(id(r,"id"),id(r,"customer_id"),r.getString("customer_name"),r.getString("customer_phone"),r.getString("name_zh"),r.getString("name_en"),r.getInt("quantity"),r.getInt("remaining"),r.getBoolean("opened"),time(r,"received_at"),time(r,"expires_at"),now.isBefore(time(r,"expires_at"))?"stored":"expired",r.getString("location_name"),r.getString("location"),r.getString("label"),r.getString("custodian_name")),args.toArray());
  return new Page(items,summary,page,size,((long)page+1)*size<summary.itemCount());
 }
}
