package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import java.sql.*;import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
@Repository
public class WorkModeRepository {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public WorkModeRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 public void siteLock(UUID tenant,UUID store){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.clock-site:"+tenant+":"+store);}
 public List<Assignment> assignments(UUID tenant,UUID employee){return jdbc.query("select * from workforce_work_modes where tenant_id=? and employee_id=? order by effective_from,version",this::assignment,tenant,employee);}
 public Map<UUID,List<Assignment>> assignmentsFor(UUID tenant,List<UUID> employees){
  if(employees.isEmpty())return Map.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(employees);var result=new HashMap<UUID,List<Assignment>>();
  jdbc.query("select * from workforce_work_modes where tenant_id=? and employee_id in ("+String.join(",",Collections.nCopies(employees.size(),"?"))+") order by effective_from,version",this::assignment,args.toArray()).forEach(a->result.computeIfAbsent(a.employeeId(),k->new ArrayList<>()).add(a));return result;
 }
 private Assignment assignment(ResultSet r,int n)throws SQLException{return new Assignment(r.getObject("id",UUID.class),r.getObject("employee_id",UUID.class),r.getObject("effective_from",LocalDate.class),r.getString("mode"),r.getString("origin"),r.getObject("position_id",UUID.class),r.getLong("position_version"),r.getLong("version"));}
 public Optional<Assignment> assignment(UUID tenant,UUID id){return jdbc.query("select * from workforce_work_modes where tenant_id=? and id=?",this::assignment,tenant,id).stream().findFirst();}
 public void insert(UUID tenant,Assignment a){jdbc.update("insert into workforce_work_modes(id,tenant_id,employee_id,effective_from,mode,origin,position_id,position_version,version) values(?,?,?,?,?,?,?,?,?)",a.id(),tenant,a.employeeId(),a.effectiveFrom(),a.mode(),a.origin(),a.positionId(),a.positionVersion(),a.version());}
 public Site site(UUID tenant,UUID store){return jdbc.query("select point,version from workforce_clock_sites where tenant_id=? and store_id=?",(r,n)->new Site(json.read(r.getString(1),Point.class),r.getLong(2)),tenant,store).stream().findFirst().orElse(new Site(null,0));}
 public void site(UUID tenant,UUID store,Site s){jdbc.update("insert into workforce_clock_sites(tenant_id,store_id,point,version) values(?,?,?::jsonb,?) on conflict(tenant_id,store_id) do update set point=excluded.point,version=excluded.version,updated_at=now()",tenant,store,json.write(s.point()),s.version());}
 public List<HomeAddress> addresses(UUID tenant,UUID employee){return jdbc.query("select * from workforce_home_addresses where tenant_id=? and employee_id=? order by effective_from,created_at,id",this::home,tenant,employee);}
 public Optional<HomeAddress> address(UUID tenant,UUID id){return jdbc.query("select * from workforce_home_addresses where tenant_id=? and id=?",this::home,tenant,id).stream().findFirst();}
 private HomeAddress home(ResultSet r,int n)throws SQLException{return new HomeAddress(r.getObject("id",UUID.class),r.getObject("employee_id",UUID.class),json.read(r.getString("point"),Point.class),r.getObject("effective_from",LocalDate.class),r.getString("status"),r.getLong("version"),r.getObject("applicant_id",UUID.class),r.getObject("reviewer_id",UUID.class),r.getString("review_note"),r.getTimestamp("created_at").toInstant());}
 public void insertHome(UUID tenant,HomeAddress h){jdbc.update("insert into workforce_home_addresses(id,tenant_id,employee_id,point,effective_from,status,version,applicant_id) values(?,?,?,?::jsonb,?,'pending',1,?)",h.id(),tenant,h.employeeId(),json.write(h.point()),h.effectiveFrom(),h.applicantId());}
 public void decide(UUID tenant,UUID id,UUID actor,HomeDecision c){jdbc.update("update workforce_home_addresses set status=?,reviewer_id=?,review_note=?,version=2,updated_at=now() where tenant_id=? and id=? and status='pending'",c.status(),actor,c.note().trim(),tenant,id);}
 public void evidence(UUID tenant,UUID event,ClockPolicy p,Location l){jdbc.update("insert into workforce_clock_locations(tenant_id,event_id,policy,location) values(?,?,?::jsonb,?::jsonb)",tenant,event,p==null?null:json.write(p),l==null?null:json.write(l));}
 public Optional<Location> location(UUID tenant,UUID event){return jdbc.query("select location from workforce_clock_locations where tenant_id=? and event_id=?",(r,n)->r.getString(1)==null?null:json.read(r.getString(1),Location.class),tenant,event).stream().filter(Objects::nonNull).findFirst();}
}
