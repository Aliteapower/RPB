package com.rpb.reservation.workforce.attendance;
import com.rpb.reservation.workforce.application.WorkforceJson;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class AttendanceRepository {
    private final JdbcTemplate jdbc;private final WorkforceJson json;
    public AttendanceRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
    public Optional<Session> bySchedule(UUID tenant,UUID schedule){return jdbc.query("select * from workforce_attendance_sessions where tenant_id=? and schedule_id=?",this::session,tenant,schedule).stream().findFirst();}
    public List<Session> sessions(UUID tenant,List<UUID> ids){if(ids.isEmpty())return List.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(ids);return jdbc.query("select * from workforce_attendance_sessions where tenant_id=? and schedule_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",this::session,args.toArray());}
    public List<Correction> correctionsFor(UUID tenant,List<UUID> ids){if(ids.isEmpty())return List.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(ids);return jdbc.query("select c.*,e.name from workforce_corrections c join workforce_employees e on e.id=c.employee_id and e.tenant_id=c.tenant_id where c.tenant_id=? and c.schedule_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by c.created_at,c.id",this::correction,args.toArray());}
    public Optional<Session> active(UUID tenant,UUID employee){return jdbc.query("select * from workforce_attendance_sessions where tenant_id=? and employee_id=? and out_at is null",this::session,tenant,employee).stream().findFirst();}
    public Optional<Session> latest(UUID tenant,UUID store,UUID employee){return jdbc.query("select * from workforce_attendance_sessions where tenant_id=? and store_id=? and employee_id=? order by in_at desc,id limit 1",this::session,tenant,store,employee).stream().findFirst();}
    private Session session(ResultSet r,int n)throws SQLException{return new Session(r.getObject("id",UUID.class),r.getObject("schedule_id",UUID.class),r.getObject("employee_id",UUID.class),r.getObject("store_id",UUID.class),time(r,"in_at"),time(r,"out_at"),time(r,"break_started_at"),List.of(json.read(r.getString("breaks"),Interval[].class)),r.getBoolean("override_break"),r.getLong("version"),r.getString("break_started_rule_id"),r.getString("clock_policy")==null?null:json.read(r.getString("clock_policy"),com.rpb.reservation.workforce.workmode.WorkModeDtos.ClockPolicy.class));}
    private Instant time(ResultSet r,String field)throws SQLException{var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
    public void save(UUID tenant,Session s){jdbc.update("""
        insert into workforce_attendance_sessions(id,tenant_id,store_id,employee_id,schedule_id,in_at,out_at,break_started_at,breaks,override_break,version,break_started_rule_id,clock_policy)
        values(?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?::jsonb) on conflict(schedule_id) do update set in_at=excluded.in_at,out_at=excluded.out_at,break_started_at=excluded.break_started_at,breaks=excluded.breaks,override_break=excluded.override_break,break_started_rule_id=excluded.break_started_rule_id,clock_policy=excluded.clock_policy,version=excluded.version,updated_at=now()
        where workforce_attendance_sessions.tenant_id=excluded.tenant_id and workforce_attendance_sessions.employee_id=excluded.employee_id and workforce_attendance_sessions.store_id=excluded.store_id
        """,s.id(),tenant,s.storeId(),s.employeeId(),s.scheduleId(),Timestamp.from(s.inAt()),s.outAt()==null?null:Timestamp.from(s.outAt()),s.breakStartedAt()==null?null:Timestamp.from(s.breakStartedAt()),json.write(s.breaks()),s.overrideBreak(),s.version(),s.breakStartedRuleId(),s.clockPolicy()==null?null:json.write(s.clockPolicy()));}
    public boolean overlaps(UUID tenant,UUID employee,UUID exceptSchedule,Instant from,Instant to){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from workforce_attendance_sessions where tenant_id=? and employee_id=? and schedule_id<>?
        and (?::timestamptz is null or in_at<?::timestamptz) and (out_at is null or out_at>?))
        """,Boolean.class,tenant,employee,exceptSchedule,to==null?null:Timestamp.from(to),to==null?null:Timestamp.from(to),Timestamp.from(from)));}
    public Optional<Session> replay(UUID tenant,UUID id,UUID employee,UUID store,ClockWrite c){return jdbc.query("select e.*,l.location command_location from workforce_clock_events e left join workforce_clock_locations l on l.tenant_id=e.tenant_id and l.event_id=e.id where e.tenant_id=? and e.id=?",(r,n)->{
        if(!employee.equals(r.getObject("employee_id",UUID.class))||!store.equals(r.getObject("store_id",UUID.class))||!c.scheduleId().equals(r.getObject("schedule_id",UUID.class))||!c.action().equals(r.getString("action"))||!Objects.equals(c.breakRuleId(),r.getString("break_rule_id"))||!Objects.equals(c.mode(),r.getString("requested_mode")))throw com.rpb.reservation.workforce.domain.WorkforceException.conflict();
        var location=r.getString("command_location")==null?null:json.read(r.getString("command_location"),com.rpb.reservation.workforce.workmode.WorkModeDtos.Location.class);if(!Objects.equals(c.location(),location))throw com.rpb.reservation.workforce.domain.WorkforceException.conflict();
        return json.read(r.getString("response_json"),Session.class);},tenant,id).stream().findFirst();}
    public void event(UUID tenant,UUID id,UUID actor,String action,Instant time,Session s){event(tenant,id,actor,action,time,s,null);}
    public void event(UUID tenant,UUID id,UUID actor,String action,Instant time,Session s,String ruleId){event(tenant,id,actor,action,time,s,ruleId,null,null);}
    public void event(UUID tenant,UUID id,UUID actor,String action,Instant time,Session s,String ruleId,String clockMode,String requestedMode){jdbc.update("insert into workforce_clock_events(id,tenant_id,store_id,employee_id,schedule_id,session_id,action,occurred_at,actor_id,response_json,break_rule_id,clock_mode,requested_mode) values(?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",id,tenant,s.storeId(),s.employeeId(),s.scheduleId(),s.id(),action,Timestamp.from(time),actor,json.write(s),ruleId,clockMode,requestedMode);}
    public List<Event> events(UUID tenant,UUID schedule){return jdbc.query("select e.id,e.action,e.occurred_at,e.actor_id,l.location,e.clock_mode from workforce_clock_events e left join workforce_clock_locations l on l.tenant_id=e.tenant_id and l.event_id=e.id where e.tenant_id=? and e.schedule_id=? order by e.occurred_at,e.id",(r,n)->new Event(r.getObject(1,UUID.class),r.getString(2),r.getTimestamp(3).toInstant(),r.getObject(4,UUID.class),r.getString(5)==null?null:json.read(r.getString(5),com.rpb.reservation.workforce.workmode.WorkModeDtos.Location.class),r.getString(6)),tenant,schedule);}
    public Optional<Correction> correction(UUID tenant,UUID id){return jdbc.query("select c.*,e.name from workforce_corrections c join workforce_employees e on e.id=c.employee_id and e.tenant_id=c.tenant_id where c.tenant_id=? and c.id=?",this::correction,tenant,id).stream().findFirst();}
    public List<Correction> corrections(UUID tenant,UUID schedule){return jdbc.query("select c.*,e.name from workforce_corrections c join workforce_employees e on e.id=c.employee_id and e.tenant_id=c.tenant_id where c.tenant_id=? and c.schedule_id=? order by c.created_at,c.id",this::correction,tenant,schedule);}
    public List<Correction> corrections(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID employee){return jdbc.query("""
        select c.*,e.name from workforce_corrections c join workforce_employees e on e.id=c.employee_id and e.tenant_id=c.tenant_id
        join workforce_schedules s on s.id=c.schedule_id and s.tenant_id=c.tenant_id where c.tenant_id=? and c.store_id=? and s.work_date between ? and ?
        and (?::uuid is null or c.employee_id=?::uuid) order by c.created_at desc,c.id limit 1000
        """,this::correction,tenant,store,from,to,employee,employee);}
    private Correction correction(ResultSet r,int n)throws SQLException{return new Correction(r.getObject("id",UUID.class),r.getObject("schedule_id",UUID.class),r.getObject("employee_id",UUID.class),r.getString("name"),r.getLong("based_session_version"),time(r,"in_at"),time(r,"out_at"),List.of(json.read(r.getString("breaks"),Interval[].class)),r.getBoolean("override_break"),r.getString("reason"),r.getString("status"),r.getObject("applicant_id",UUID.class),r.getObject("reviewer_id",UUID.class),r.getString("review_note"),r.getLong("version"),time(r,"created_at"));}
    public void create(UUID tenant,UUID store,Correction c){jdbc.update("insert into workforce_corrections(id,tenant_id,store_id,employee_id,schedule_id,based_session_version,in_at,out_at,breaks,override_break,reason,status,applicant_id,version) values(?,?,?,?,?,?,?,?,?::jsonb,?,?,'pending',?,1)",c.id(),tenant,store,c.employeeId(),c.scheduleId(),c.basedSessionVersion(),Timestamp.from(c.inAt()),Timestamp.from(c.outAt()),json.write(c.breaks()),c.overrideBreak(),c.reason(),c.applicantId());}
    public void decision(UUID tenant,UUID id,UUID actor,DecisionWrite c,long version){jdbc.update("update workforce_corrections set status=?,reviewer_id=?,review_note=?,version=?,updated_at=now() where tenant_id=? and id=?",c.status(),actor,c.note().trim(),version,tenant,id);}
}


