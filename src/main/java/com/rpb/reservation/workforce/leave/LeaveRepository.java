package com.rpb.reservation.workforce.leave;

import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LeaveRepository {
    private final JdbcTemplate jdbc;
    public LeaveRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static final String SELECT="select l.*,e.name employee_name from workforce_leaves l join workforce_employees e on e.id=l.employee_id and e.tenant_id=l.tenant_id ";
    public boolean overlapsDailyException(UUID tenant,UUID employee,Instant start,Instant end){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_attendance_exceptions where tenant_id=? and employee_id=? and status in ('pending','approved','cancel_requested') and start_at<? and end_at>?)",Boolean.class,tenant,employee,Timestamp.from(end),Timestamp.from(start)));}
    public Optional<Leave> find(UUID tenant,UUID id){return jdbc.query(SELECT+"where l.tenant_id=? and l.id=?",this::map,tenant,id).stream().findFirst();}
    public List<Leave> leaves(UUID tenant,UUID store,LocalDate from,LocalDate to,UUID employee){return jdbc.query(SELECT+"join workforce_schedules s on s.id=l.schedule_id and s.tenant_id=l.tenant_id where l.tenant_id=? and l.store_id=? and s.work_date between ? and ? and (?::uuid is null or l.employee_id=?::uuid) order by s.work_date,l.start_at,l.id limit 1001",this::map,tenant,store,from,to,employee,employee);}
    public List<Leave> forSchedule(UUID tenant,UUID schedule){return jdbc.query(SELECT+"where l.tenant_id=? and l.schedule_id=? order by l.start_at,l.id",this::map,tenant,schedule);}
    public List<Leave> forGroup(UUID tenant,UUID store,UUID group){return jdbc.query(SELECT+"join workforce_leave_group_members m on m.leave_id=l.id and m.tenant_id=l.tenant_id and m.store_id=l.store_id where m.tenant_id=? and m.store_id=? and m.group_id=? order by l.start_at,l.id",this::map,tenant,store,group);}
    public List<Leave> forSchedules(UUID tenant,List<UUID> ids){if(ids.isEmpty())return List.of();var args=new ArrayList<Object>();args.add(tenant);args.addAll(ids);return jdbc.query(SELECT+"where l.tenant_id=? and l.schedule_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by l.start_at,l.id",this::map,args.toArray());}
    public String lastAction(UUID tenant,UUID id){return jdbc.queryForObject("select last_action from workforce_leaves where tenant_id=? and id=?",String.class,tenant,id);}
    public void create(UUID tenant,UUID store,Leave l){jdbc.update("""
        insert into workforce_leaves(id,tenant_id,store_id,schedule_id,employee_id,type_id,type_name,paid,start_at,end_at,reason,status,applicant_id,version,created_at,medical_reference,last_action)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'submit')
        """,l.id(),tenant,store,l.scheduleId(),l.employeeId(),l.typeId(),l.typeName(),l.paid(),Timestamp.from(l.startAt()),Timestamp.from(l.endAt()),l.reason(),l.status(),l.applicantId(),l.version(),Timestamp.from(l.createdAt()),l.medicalReference());}
    public void decision(UUID tenant,UUID id,String status,String action,UUID reviewer,String note,long version){jdbc.update("update workforce_leaves set status=?,last_action=?,reviewer_id=?,review_note=?,version=?,updated_at=now() where tenant_id=? and id=?",status,action,reviewer,note,version,tenant,id);}
    private Leave map(ResultSet r,int n)throws SQLException{return new Leave(r.getObject("id",UUID.class),r.getObject("schedule_id",UUID.class),r.getObject("employee_id",UUID.class),r.getString("employee_name"),r.getObject("type_id",UUID.class),r.getString("type_name"),r.getBoolean("paid"),r.getTimestamp("start_at").toInstant(),r.getTimestamp("end_at").toInstant(),r.getString("reason"),r.getString("status"),r.getObject("applicant_id",UUID.class),r.getObject("reviewer_id",UUID.class),r.getString("review_note"),r.getLong("version"),r.getTimestamp("created_at").toInstant(),r.getString("medical_reference"));}
}
