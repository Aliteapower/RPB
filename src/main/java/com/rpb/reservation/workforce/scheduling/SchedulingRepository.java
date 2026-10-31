package com.rpb.reservation.workforce.scheduling;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class SchedulingRepository {
    private final JdbcTemplate jdbc;private final WorkforceJson json;
    public SchedulingRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
    public void lock(String key){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,key);}
    /** Shared for ordinary writes; exclusive for a complete store-week copy snapshot. Take before employee/shift locks. */
    public void lockPlanning(UUID tenant,UUID store,boolean exclusive){jdbc.queryForObject("select "+(exclusive?"pg_advisory_xact_lock":"pg_advisory_xact_lock_shared")+"(hashtextextended(?,0))",Object.class,"workforce.schedule-plan:"+tenant+":"+store);}
    public record Inspection(UUID store,Schedule schedule,boolean deleted,boolean used,int segments) {}
    public List<Inspection> inspect(UUID tenant,List<UUID> ids){
        var sql="""
            select s.*,e.name,e.employee_no,
             (exists(select 1 from workforce_attendance_sessions a where a.tenant_id=s.tenant_id and a.schedule_id=s.id)
              or exists(select 1 from workforce_corrections c where c.tenant_id=s.tenant_id and c.schedule_id=s.id and c.status='pending')
              or exists(select 1 from workforce_leaves l where l.tenant_id=s.tenant_id and l.schedule_id=s.id and l.status in ('pending','approved','cancel_requested'))) as in_use,
             (select count(*) from workforce_schedules m where m.tenant_id=s.tenant_id and m.store_id=s.store_id and m.employee_id=s.employee_id and m.work_date=s.work_date and m.shift_id=s.shift_id and m.status='scheduled' and m.deleted_at is null) as segments
            from workforce_schedules s join workforce_employees e on e.tenant_id=s.tenant_id and e.id=s.employee_id
            where s.tenant_id=:tenant and s.id in (:ids) order by e.employee_no,s.id
            """;
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query(sql,new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("tenant",tenant).addValue("ids",ids),(r,n)->new Inspection(r.getObject("store_id",UUID.class),schedule(r,n),r.getTimestamp("deleted_at")!=null,r.getBoolean("in_use"),r.getInt("segments")));
    }
    public List<Schedule> copyCandidates(UUID tenant,UUID store,LocalDate from){return jdbc.query("select s.*,e.name,e.employee_no from workforce_schedules s join workforce_employees e on e.id=s.employee_id and e.tenant_id=s.tenant_id where s.tenant_id=? and s.store_id=? and s.work_date between ? and ? and s.status='scheduled' and s.deleted_at is null order by e.employee_no,s.work_date,s.start_at,s.id limit 5001",this::schedule,tenant,store,from,from.plusDays(6));}
    public List<Schedule> forEmployees(UUID tenant,UUID store,LocalDate from,LocalDate to,List<UUID> ids){
        if(ids.isEmpty())return List.of();
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("select s.*,e.name,e.employee_no from workforce_schedules s join workforce_employees e on e.tenant_id=s.tenant_id and e.id=s.employee_id where s.tenant_id=:tenant and s.store_id=:store and s.deleted_at is null and s.work_date between :from and :to and s.employee_id in (:ids) and (s.status='scheduled' or (not exists(select 1 from workforce_schedules a where a.tenant_id=s.tenant_id and a.store_id=s.store_id and a.employee_id=s.employee_id and a.work_date=s.work_date and a.status='scheduled') and not exists(select 1 from workforce_schedules h where h.tenant_id=s.tenant_id and h.store_id=s.store_id and h.employee_id=s.employee_id and h.work_date=s.work_date and h.status='cancelled' and (h.updated_at,h.created_at,h.id)>(s.updated_at,s.created_at,s.id)))) order by s.work_date,s.start_at,s.id",new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("tenant",tenant).addValue("store",store).addValue("from",from).addValue("to",to).addValue("ids",ids),this::schedule);
    }
    public String timezone(UUID tenant,UUID store){return jdbc.queryForObject("select timezone from stores where tenant_id=? and id=? and status='active' and deleted_at is null",String.class,tenant,store);}
    public List<Shift> shifts(UUID tenant,UUID store){return jdbc.query("select snapshot from workforce_shifts where tenant_id=? and store_id=? order by name,id",(r,n)->json.read(r.getString(1),Shift.class),tenant,store);}
    public Optional<Shift> shift(UUID tenant,UUID store,UUID id){return shifts(tenant,store).stream().filter(s->s.id().equals(id)).findFirst();}
    public void saveShift(UUID tenant,UUID store,Shift s){int changed=jdbc.update("""
        insert into workforce_shifts(id,tenant_id,store_id,name,snapshot,enabled,version) values(?,?,?,?,?::jsonb,?,?)
        on conflict(id) do update set name=excluded.name,snapshot=excluded.snapshot,enabled=excluded.enabled,version=excluded.version,updated_at=now()
        where workforce_shifts.tenant_id=excluded.tenant_id and workforce_shifts.store_id=excluded.store_id
        """,s.id(),tenant,store,s.name(),json.write(s),s.enabled(),s.version());if(changed!=1)throw com.rpb.reservation.workforce.domain.WorkforceException.conflict();}
    public long ruleTemplateVersion(UUID tenant,UUID id){return jdbc.queryForList("select version from workforce_attendance_rule_templates where tenant_id=? and id=?",Long.class,tenant,id).stream().findFirst().orElse(0L);}
    public Optional<Schedule> find(UUID tenant,UUID id){return jdbc.query("select s.*,e.name,e.employee_no from workforce_schedules s join workforce_employees e on e.id=s.employee_id and e.tenant_id=s.tenant_id where s.tenant_id=? and s.id=?",this::schedule,tenant,id).stream().findFirst();}
    public UUID store(UUID tenant,UUID id){return jdbc.queryForObject("select store_id from workforce_schedules where tenant_id=? and id=?",UUID.class,tenant,id);}
    public List<Schedule> list(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID employee,int limit,int offset){
        return jdbc.query("""
            select s.*,e.name,e.employee_no from workforce_schedules s join workforce_employees e on e.id=s.employee_id and e.tenant_id=s.tenant_id
            where s.tenant_id=? and s.store_id=? and s.deleted_at is null and s.work_date between ? and ? and (?::uuid is null or s.employee_id=?::uuid)
            order by s.work_date,s.start_at,e.employee_no,s.id limit ? offset ?
            """,this::schedule,tenant,store,from,to,employee,employee,limit,offset);
    }
    public record ScopedSchedule(UUID store,Schedule schedule) {}
    public List<ScopedSchedule> scoped(UUID tenant,List<UUID> ids){if(ids.isEmpty())return List.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(ids);return jdbc.query("select s.*,e.name,e.employee_no from workforce_schedules s join workforce_employees e on e.id=s.employee_id and e.tenant_id=s.tenant_id where s.tenant_id=? and s.id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",(r,n)->new ScopedSchedule(r.getObject("store_id",UUID.class),schedule(r,n)),args.toArray());}
    private Schedule schedule(ResultSet r,int n)throws SQLException{return new Schedule(r.getObject("id",UUID.class),r.getObject("employee_id",UUID.class),r.getString("name"),r.getString("employee_no"),r.getObject("shift_id",UUID.class),r.getObject("work_date",LocalDate.class),r.getTimestamp("start_at").toInstant(),r.getTimestamp("end_at").toInstant(),r.getString("timezone"),r.getString("status"),json.read(r.getString("shift_snapshot"),Shift.class),r.getObject("company_id",UUID.class),r.getLong("version"));}
    public void save(UUID tenant,UUID store,Schedule s){int changed=jdbc.update("""
        insert into workforce_schedules(id,tenant_id,store_id,employee_id,company_id,shift_id,work_date,start_at,end_at,timezone,shift_snapshot,status,version)
        values(?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?) on conflict(id) do update set shift_id=excluded.shift_id,work_date=excluded.work_date,start_at=excluded.start_at,end_at=excluded.end_at,timezone=excluded.timezone,shift_snapshot=excluded.shift_snapshot,company_id=excluded.company_id,status=excluded.status,version=excluded.version,updated_at=now()
        where workforce_schedules.tenant_id=excluded.tenant_id and workforce_schedules.store_id=excluded.store_id and workforce_schedules.employee_id=excluded.employee_id
        """,s.id(),tenant,store,s.employeeId(),s.companyId(),s.shiftId(),s.workDate(),Timestamp.from(s.startAt()),Timestamp.from(s.endAt()),s.timezone(),json.write(s.shift()),s.status(),s.version());if(changed!=1)throw com.rpb.reservation.workforce.domain.WorkforceException.conflict();}
    public List<Schedule> copySource(UUID tenant,UUID store,LocalDate from){return jdbc.query("select s.*,e.name,e.employee_no from workforce_schedules s join workforce_employees e on e.id=s.employee_id and e.tenant_id=s.tenant_id where s.tenant_id=? and s.store_id=? and s.work_date between ? and ? and s.status='scheduled' and s.deleted_at is null order by s.start_at,s.id limit 101",this::schedule,tenant,store,from,from.plusDays(6));}
    public boolean assigned(UUID tenant,UUID employee,UUID store,LocalDate date){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from workforce_employees e join workforce_work_stores w on w.employee_id=e.id and w.tenant_id=e.tenant_id
        where e.tenant_id=? and e.id=? and e.status='active' and e.enabled=true and e.hire_date<=? and (e.leave_date is null or e.leave_date>=?)
        and w.store_id=? and w.started_on<=? and (w.ended_on is null or w.ended_on>?))
        """,Boolean.class,tenant,employee,date,date,store,date,date));}
    public boolean hasStoreAssignment(UUID tenant,UUID employee,UUID store){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_work_stores where tenant_id=? and employee_id=? and store_id=?)",Boolean.class,tenant,employee,store));}
    /** Candidate dates intersect employment and the store assignment; ended_on is exclusive. */
    public List<Option> employees(UUID tenant,UUID store,LocalDate from,LocalDate to){return jdbc.query("""
        select e.id,e.name||' · '||e.employee_no from workforce_employees e
        where e.tenant_id=? and e.status='active' and e.enabled=true
          and exists(select 1 from workforce_work_stores w
            where w.tenant_id=e.tenant_id and w.employee_id=e.id and w.store_id=?
              and greatest(e.hire_date,w.started_on,?) <= least(coalesce(e.leave_date,'infinity'::date),coalesce(w.ended_on-1,'infinity'::date),?))
        order by e.employee_no
        """,(r,n)->new Option(r.getObject(1,UUID.class),r.getString(2)),tenant,store,from,to);}
    public boolean overlaps(UUID tenant,UUID employee,UUID except,Instant from,Instant to){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_schedules where tenant_id=? and employee_id=? and id<>? and status='scheduled' and start_at<? and end_at>?)",Boolean.class,tenant,employee,except,Timestamp.from(to),Timestamp.from(from)));}
    public boolean deleted(UUID tenant,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select deleted_at is not null from workforce_schedules where tenant_id=? and id=?",Boolean.class,tenant,id));}
    public void softDelete(UUID tenant,UUID store,UUID id,long version){if(jdbc.update("update workforce_schedules set status='cancelled',deleted_at=now(),updated_at=now(),version=version+1 where tenant_id=? and store_id=? and id=? and version=? and deleted_at is null",tenant,store,id,version)!=1)throw com.rpb.reservation.workforce.domain.WorkforceException.conflict();}
    public boolean used(UUID tenant,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_attendance_sessions where tenant_id=? and schedule_id=?) or exists(select 1 from workforce_corrections where tenant_id=? and schedule_id=? and status='pending') or exists(select 1 from workforce_leaves where tenant_id=? and schedule_id=? and status in ('pending','approved','cancel_requested'))",Boolean.class,tenant,id,tenant,id,tenant,id));}
    public Optional<List<Schedule>> copied(UUID tenant,UUID store,UUID id,CopyWrite c){
        return jdbc.query("select * from workforce_schedule_copies where tenant_id=? and id=?",(r,n)->{
            if(!store.equals(r.getObject("store_id",UUID.class))||!c.sourceWeek().equals(r.getObject("source_week",LocalDate.class))||!c.targetWeek().equals(r.getObject("target_week",LocalDate.class)))throw com.rpb.reservation.workforce.domain.WorkforceException.conflict();
            return List.of(json.read(r.getString("response_json"),Schedule[].class));},tenant,id).stream().findFirst();
    }
    public void copied(UUID tenant,UUID store,UUID actor,UUID id,CopyWrite c,List<Schedule> result){jdbc.update("insert into workforce_schedule_copies(id,tenant_id,store_id,source_week,target_week,response_json,actor_id) values(?,?,?,?,?,?::jsonb,?)",id,tenant,store,c.sourceWeek(),c.targetWeek(),json.write(result),actor);}
}

