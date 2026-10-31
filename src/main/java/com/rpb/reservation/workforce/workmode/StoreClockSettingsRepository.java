package com.rpb.reservation.workforce.workmode;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository
public class StoreClockSettingsRepository {
 public record Identity(UUID storeId,String storeCode,String storeName,UUID entityId,String entityName,String address,long version){}
 private final JdbcTemplate jdbc;
 public StoreClockSettingsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Identity identity(UUID tenant,UUID store,boolean lock){
  return jdbc.query("""
   select s.id,s.store_code,s.display_name,s.operating_entity_id,e.display_name entity_name,
    coalesce(s.share_address,'') address,s.version from stores s
   left join operating_entities e on e.id=s.operating_entity_id and e.tenant_id=s.tenant_id and e.deleted_at is null
   where s.tenant_id=? and s.id=? and s.deleted_at is null
   """+(lock?" for update of s":""),(r,n)->new Identity(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,UUID.class),r.getString(5),r.getString(6),r.getLong(7)),tenant,store).stream().findFirst().orElseThrow(WorkforceException::missing);
 }
 public void address(UUID tenant,Identity before,String address){
  if(jdbc.update("update stores set share_address=?,version=version+1,updated_at=now() where tenant_id=? and id=? and version=? and deleted_at is null",address,tenant,before.storeId(),before.version())!=1)throw WorkforceException.conflict();
 }
}
