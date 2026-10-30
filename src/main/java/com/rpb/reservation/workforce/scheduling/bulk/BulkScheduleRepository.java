package com.rpb.reservation.workforce.scheduling.bulk;

import static com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class BulkScheduleRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final WorkforceJson json;
    public BulkScheduleRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc);this.json=json;}
    public List<com.rpb.reservation.workforce.calendar.CalendarDtos.CalendarEvent> calendar(UUID tenant,UUID store,LocalDate from,LocalDate to){return new com.rpb.reservation.workforce.calendar.CalendarRepository(jdbc).list(tenant,store,from,to);}
    public List<com.rpb.reservation.workforce.scheduling.restswap.RestSwapDtos.Link> restSwaps(UUID tenant,UUID store,LocalDate from,LocalDate to,List<UUID> employees){return new com.rpb.reservation.workforce.scheduling.restswap.RestSwapRepository(jdbc).list(tenant,store,from,to,null).stream().filter(r->employees.contains(r.employeeId())).toList();}
    public record State(UUID id,String employeeNo,String name,UUID positionId,String status,LocalDate hireDate,LocalDate leaveDate,long version) {}
    public record Assignment(UUID employeeId,LocalDate started,LocalDate ended) {}
    public record Occupied(UUID id,UUID employeeId,UUID storeId,LocalDate date,Instant start,Instant end,long version) {}
    private MapSqlParameterSource params(UUID tenant,UUID store){return new MapSqlParameterSource("tenant",tenant).addValue("store",store);}
    public void lockStore(UUID tenant,UUID store){if(jdbc.queryForList("select id from stores where tenant_id=? and id=? and status='active' and deleted_at is null for share",UUID.class,tenant,store).isEmpty())throw WorkforceException.missing();}
    private String scope(boolean board){return """
        e.tenant_id=:tenant and (:position::uuid is null or e.position_id=:position::uuid)
        and (e.name ilike :search or e.employee_no ilike :search)
        and ((e.status='active' and e.enabled=true and exists(select 1 from workforce_work_stores w where w.tenant_id=e.tenant_id and w.employee_id=e.id and w.store_id=:store and w.ended_on is null))
        """+(board?" or exists(select 1 from workforce_schedules s where s.tenant_id=e.tenant_id and s.employee_id=e.id and s.store_id=:store and s.work_date between :from and :to)":"")+")";}
    public record Page(List<Employee> items,long total) {}
    public Page page(UUID tenant,UUID store,UUID position,String search,int limit,int offset,LocalDate from){return page(tenant,store,position,search,limit,offset,from,from==null?null:from.plusDays(6));}
    public Page page(UUID tenant,UUID store,UUID position,String search,int limit,int offset,LocalDate from,LocalDate to){
        var p=params(tenant,store).addValue("position",position).addValue("search","%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%").addValue("limit",limit).addValue("offset",offset);
        if(from!=null)p.addValue("from",from).addValue("to",to);
        String where=scope(from!=null);
        long total=named.queryForObject("select count(*) from workforce_employees e where "+where,p,Long.class);
        var items=named.query("select e.id,e.employee_no,e.name,e.position_id from workforce_employees e where "+where+" order by e.employee_no,e.id limit :limit offset :offset",p,(r,n)->new Employee(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,UUID.class)));
        return new Page(items,total);
    }
    public List<Position> positions(UUID tenant){return jdbc.query("select id,name,enabled from workforce_positions where tenant_id=? and deleted_at is null order by name,id",(r,n)->new Position(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3)),tenant);}
    public List<State> states(UUID tenant,List<UUID> ids){return named.query("select id,employee_no,name,position_id,case when enabled then status else 'disabled' end as status,hire_date,leave_date,version from workforce_employees where tenant_id=:tenant and id in (:ids) order by id",new MapSqlParameterSource("tenant",tenant).addValue("ids",ids),(r,n)->new State(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,UUID.class),r.getString(5),r.getObject(6,LocalDate.class),r.getObject(7,LocalDate.class),r.getLong(8)));}
    public List<Assignment> assignments(UUID tenant,UUID store,List<UUID> ids){return named.query("select employee_id,started_on,ended_on from workforce_work_stores where tenant_id=:tenant and store_id=:store and employee_id in (:ids) order by employee_id,started_on,ended_on",params(tenant,store).addValue("ids",ids),(r,n)->new Assignment(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class),r.getObject(3,LocalDate.class)));}
    public List<Occupied> occupied(UUID tenant,List<UUID> ids,LocalDate from,LocalDate to,Instant start,Instant end){return named.query("select id,employee_id,store_id,work_date,start_at,end_at,version from workforce_schedules where tenant_id=:tenant and employee_id in (:ids) and status='scheduled' and (work_date between :from and :to or (start_at<:end and end_at>:start)) order by id",new MapSqlParameterSource("tenant",tenant).addValue("ids",ids).addValue("from",from).addValue("to",to).addValue("start",java.sql.Timestamp.from(start)).addValue("end",java.sql.Timestamp.from(end)),(r,n)->new Occupied(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,LocalDate.class),r.getTimestamp(5).toInstant(),r.getTimestamp(6).toInstant(),r.getLong(7)));}
    private MapSqlParameterSource staffingParams(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID position,String search){
        return params(tenant,store).addValue("from",from).addValue("to",to).addValue("position",position).addValue("search","%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");
    }
    private String staffingScope(){
        return """
            from workforce_schedules s join workforce_employees e on e.tenant_id=s.tenant_id and e.id=s.employee_id
            where s.tenant_id=:tenant and s.store_id=:store and s.work_date between :from and :to
            and s.status='scheduled' and s.deleted_at is null
            and (:position::uuid is null or e.position_id=:position::uuid)
            and (e.name ilike :search or e.employee_no ilike :search)
            """;
    }
    public Staffing staffing(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID position,String search){
        var p=staffingParams(tenant,store,from,to,position,search);
        String scope=staffingScope();
        var shifts=named.query("select s.shift_id,(array_agg(s.shift_snapshot->>'name' order by s.work_date desc,s.start_at desc,s.id desc))[1],count(distinct s.employee_id),count(distinct (s.employee_id,s.work_date)) "+scope+" group by s.shift_id order by 2,s.shift_id",p,(r,n)->new ShiftCount(r.getObject(1,UUID.class),r.getString(2),r.getLong(3),r.getLong(4)));
        var days=named.query("select s.shift_id,s.work_date,count(distinct s.employee_id) "+scope+" group by s.shift_id,s.work_date order by s.work_date,s.shift_id",p,(r,n)->new ShiftDayCount(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class),r.getLong(3)));
        return new Staffing(shifts,days);
    }
    public StaffingEmployees staffingEmployees(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID shift,UUID position,String search,int limit,int offset){
        var p=staffingParams(tenant,store,from,to,position,search).addValue("shift",shift).addValue("limit",limit).addValue("offset",offset);
        String scope=staffingScope()+" and s.shift_id=:shift ";
        var counts=named.queryForMap("select count(distinct (s.employee_id,s.work_date)) as total,count(distinct s.employee_id) as people "+scope,p);
        var items=named.query("select e.id,e.employee_no,e.name,s.work_date,(array_agg(s.shift_snapshot->>'name' order by s.start_at desc,s.id desc))[1],jsonb_agg(jsonb_build_object('id',s.id,'startAt',s.start_at,'endAt',s.end_at,'timezone',s.timezone,'version',s.version) order by s.start_at,s.id)::text "+scope+" group by e.id,e.employee_no,e.name,s.work_date order by s.work_date,e.employee_no,e.id limit :limit offset :offset",p,(r,n)->new StaffingEmployee(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,LocalDate.class),r.getString(5),List.of(json.read(r.getString(6),StaffingSchedule[].class))));
        return new StaffingEmployees(items,((Number)counts.get("total")).longValue(),((Number)counts.get("people")).longValue(),limit,offset);
    }
    public long periodCount(UUID tenant,UUID store,LocalDate from,LocalDate to){return jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=? and store_id=? and work_date between ? and ? and status='scheduled'",Long.class,tenant,store,from,to);}
    public long weekCount(UUID tenant,UUID store,LocalDate from){return jdbc.queryForObject("select count(*) from workforce_schedules where tenant_id=? and store_id=? and work_date between ? and ? and status='scheduled'",Long.class,tenant,store,from,from.plusDays(6));}
    public Optional<Result> replay(UUID tenant,UUID store,UUID actor,UUID id,String hash){return replayAs(tenant,store,actor,id,hash,Result.class);}
    public <T>Optional<T> replayAs(UUID tenant,UUID store,UUID actor,UUID id,String hash,Class<T> type){return jdbc.query("select store_id,actor_id,request_hash,result_json from workforce_bulk_schedule_commands where tenant_id=? and id=?",(r,n)->{if(!store.equals(r.getObject(1,UUID.class))||!actor.equals(r.getObject(2,UUID.class))||!hash.equals(r.getString(3)))throw WorkforceException.conflict();return json.read(r.getString(4),type);},tenant,id).stream().findFirst();}
    public void receipt(UUID tenant,UUID store,UUID actor,UUID id,String hash,Result result){receiptValue(tenant,store,actor,id,hash,result);}
    public void receiptValue(UUID tenant,UUID store,UUID actor,UUID id,String hash,Object result){jdbc.update("insert into workforce_bulk_schedule_commands(id,tenant_id,store_id,actor_id,request_hash,result_json) values(?,?,?,?,?,?::jsonb)",id,tenant,store,actor,hash,json.write(result));}
}
