package com.rpb.reservation.workforce.positions;
import static com.rpb.reservation.workforce.positions.PositionDtos.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PositionRepository {
 private final JdbcTemplate jdbc;
 public PositionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 /** Also serializes employee assignment, rename, disable and reference checks. */
 public void lock(UUID tenant){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.positions:"+tenant);}
 public List<Position> list(UUID tenant){return jdbc.query("select id,name,enabled,version,attendance_mode,attendance_modes from workforce_positions where tenant_id=? and deleted_at is null order by lower(name),id",(r,n)->new Position(r.getObject("id",UUID.class),r.getString("name"),r.getBoolean("enabled"),r.getLong("version"),r.getString("attendance_mode"),r.getArray("attendance_modes")==null?null:List.of((String[])r.getArray("attendance_modes").getArray())),tenant);}
 public Optional<Position> find(UUID tenant,UUID id){return list(tenant).stream().filter(p->p.id().equals(id)).findFirst();}
 public Optional<Deleted> deleted(UUID tenant,UUID id){return jdbc.query("select id,version from workforce_positions where tenant_id=? and id=? and deleted_at is not null",(r,n)->new Deleted(r.getObject("id",UUID.class),true,r.getLong("version")),tenant,id).stream().findFirst();}
 public void save(UUID tenant,Position p,boolean create){
  if(create)jdbc.update("insert into workforce_positions(id,tenant_id,name,enabled,version,attendance_mode,attendance_modes) values(?,?,?,?,?,?,?)",p.id(),tenant,p.name(),p.enabled(),p.version(),p.attendanceMode(),p.attendanceModes()==null?null:p.attendanceModes().toArray(String[]::new));
  else jdbc.update("update workforce_positions set name=?,enabled=?,version=?,attendance_mode=?,attendance_modes=?,updated_at=now() where tenant_id=? and id=? and deleted_at is null",p.name(),p.enabled(),p.version(),p.attendanceMode(),p.attendanceModes()==null?null:p.attendanceModes().toArray(String[]::new),tenant,p.id());
 }
 public boolean used(UUID tenant,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_employees where tenant_id=? and position_id=?)",Boolean.class,tenant,id));}
 public void delete(UUID tenant,UUID id,long version){jdbc.update("update workforce_positions set enabled=false,deleted_at=now(),updated_at=now(),version=? where tenant_id=? and id=? and deleted_at is null",version,tenant,id);}
}
