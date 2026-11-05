package com.rpb.reservation.workforce.attendance.daily;

import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
import com.rpb.reservation.workforce.application.WorkforceJson;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;

@Repository
public class AttendanceNormalEvaluationRepository {
 public record Scope(UUID tenant,UUID store,UUID company,UUID employee,String timezone) {
  public String cursor(){return tenant+":"+store+":"+employee+":"+company;}
 }
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 public AttendanceNormalEvaluationRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;}
 /** Keyset pages only explicitly adopted policies; no legacy attendance backfill. */
 public List<Scope> scopes(String after,int limit){return jdbc.query("select distinct on(p.tenant_id,p.source_store_id,p.employee_id,p.company_id) p.tenant_id,p.source_store_id,p.company_id,p.employee_id,coalesce(p.timezone,s.timezone) from workforce_attendance_policies p join stores s on s.tenant_id=p.tenant_id and s.id=p.source_store_id and s.operating_entity_id=p.company_id where p.employee_id is not null and s.deleted_at is null and (p.tenant_id::text||':'||p.source_store_id::text||':'||p.employee_id::text||':'||p.company_id::text)>? order by p.tenant_id,p.source_store_id,p.employee_id,p.company_id,p.effective_from desc,p.version desc limit ?",(r,n)->new Scope(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getString(5)),after,limit);}
 public boolean adopted(Scope s,LocalDate date){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_attendance_policies p where p.tenant_id=? and p.employee_id=? and p.company_id=? and p.source_store_id=? and p.effective_from<=? and not exists(select 1 from workforce_attendance_policies newer where newer.tenant_id=p.tenant_id and newer.employee_id=p.employee_id and newer.effective_from<=? and (newer.effective_from,newer.version)>(p.effective_from,p.version))) and exists(select 1 from workforce_work_stores w where w.tenant_id=? and w.employee_id=? and w.store_id=? and w.started_on<=? and (w.ended_on is null or w.ended_on>?)) and exists(select 1 from workforce_company_history c where c.tenant_id=? and c.employee_id=? and c.company_id=? and c.effective_date<=? and not exists(select 1 from workforce_company_history newer where newer.tenant_id=c.tenant_id and newer.employee_id=c.employee_id and newer.effective_date<=? and (newer.effective_date,newer.version)>(c.effective_date,c.version)))",Boolean.class,s.tenant(),s.employee(),s.company(),s.store(),date,date,s.tenant(),s.employee(),s.store(),date,date,s.tenant(),s.employee(),s.company(),date,date));}
 public void lockSources(UUID tenant){jdbc.queryForObject("select pg_advisory_xact_lock_shared(hashtextextended(?,0))",Object.class,"workforce.payroll.source:"+tenant);}
 public void save(Scope s,EffectiveDay day){jdbc.update("insert into workforce_attendance_days(id,tenant_id,company_id,employee_id,work_date) values(?,?,?,?,?) on conflict(tenant_id,company_id,employee_id,work_date) do nothing",day.sourceId(),s.tenant(),s.company(),s.employee(),day.workDate());jdbc.update("insert into workforce_attendance_day_versions(tenant_id,day_id,version,source_fingerprint,mode,state,snapshot,actor_id,initiator_kind) values(?,?,?,?,?,?,?::jsonb,null,'system')",s.tenant(),day.sourceId(),day.version(),day.sourceFingerprint(),day.mode(),day.state(),json.write(day));}
}
