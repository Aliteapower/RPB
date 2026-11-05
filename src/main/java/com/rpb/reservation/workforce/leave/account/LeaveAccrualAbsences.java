package com.rpb.reservation.workforce.leave.account;
import com.rpb.reservation.workforce.application.WorkforceJson;import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import com.rpb.reservation.workforce.approval.ApprovalCalculator;import java.util.*;import java.time.*;import java.math.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;
/** Annual-service exclusions only. Uses the existing leave/break calculator, never wage deductions. */
@Repository public class LeaveAccrualAbsences {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 private final org.springframework.beans.factory.ObjectProvider<com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService> daily;
 private final com.rpb.reservation.workforce.compliance.ComplianceRepository contracts;
 public LeaveAccrualAbsences(JdbcTemplate jdbc,WorkforceJson json){this(jdbc,json,null,null);}
 @org.springframework.beans.factory.annotation.Autowired public LeaveAccrualAbsences(JdbcTemplate jdbc,WorkforceJson json,org.springframework.beans.factory.ObjectProvider<com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService> daily,com.rpb.reservation.workforce.compliance.ComplianceRepository contracts){this.jdbc=jdbc;this.json=json;this.daily=daily;this.contracts=contracts;}
 public Map<LocalDate,BigDecimal> days(UUID tenant,UUID company,UUID employee,LocalDate from,LocalDate to){
  var schedules=new LinkedHashMap<UUID,Schedule>();var slices=new HashMap<UUID,List<ApprovalCalculator.LeaveSlice>>();
  jdbc.query("""
   select s.*,l.start_at leave_start,l.end_at leave_end from workforce_schedules s
   left join workforce_leaves l on l.tenant_id=s.tenant_id and l.schedule_id=s.id and not l.paid and l.status in('approved','cancel_requested')
   where s.tenant_id=? and s.company_id=? and s.employee_id=? and s.status='scheduled' and s.work_date between ? and ? order by s.work_date,s.id,l.start_at
   """,r->{var id=r.getObject("id",UUID.class);var shift=json.read(r.getString("shift_snapshot"),Shift.class);var s=new Schedule(id,employee,"","",r.getObject("shift_id",UUID.class),r.getObject("work_date",LocalDate.class),r.getTimestamp("start_at").toInstant(),r.getTimestamp("end_at").toInstant(),r.getString("timezone"),"scheduled",shift,company,r.getLong("version"));schedules.putIfAbsent(id,s);if(r.getTimestamp("leave_start")!=null)slices.computeIfAbsent(id,k->new ArrayList<>()).add(new ApprovalCalculator.LeaveSlice(r.getTimestamp("leave_start").toInstant(),r.getTimestamp("leave_end").toInstant(),true));},tenant,company,employee,from,to);
  var totals=new HashMap<LocalDate,long[]>();var calc=new ApprovalCalculator();for(var s:schedules.values()){var day=totals.computeIfAbsent(s.workDate(),k->new long[2]);day[0]+=calc.calculate(s,null,List.of(new ApprovalCalculator.LeaveSlice(s.startAt(),s.endAt(),true)),s.endAt(),false).paidLeaveSeconds();day[1]+=calc.calculate(s,null,slices.getOrDefault(s.id(),List.of()),s.endAt(),false).paidLeaveSeconds();}
  var result=new TreeMap<LocalDate,BigDecimal>();for(var e:totals.entrySet())if(e.getValue()[0]>0&&e.getValue()[1]>0)result.put(e.getKey(),BigDecimal.valueOf(e.getValue()[1]).divide(BigDecimal.valueOf(e.getValue()[0]),12,RoundingMode.HALF_UP).min(BigDecimal.ONE));
  if(daily!=null){var measuring=daily.getObject();
   record Interval(LocalDate date,Instant start,Instant end){}
   var intervals=jdbc.query("select work_date,start_at,end_at from workforce_attendance_exceptions where tenant_id=? and company_id=? and employee_id=? and kind='leave' and not paid and status in('approved','cancel_requested') and work_date between ? and ? order by work_date,start_at",(r,n)->new Interval(r.getObject(1,LocalDate.class),r.getTimestamp(2).toInstant(),r.getTimestamp(3).toInstant()),tenant,company,employee,from,to);
   for(var interval:intervals){var c=contracts.contract(tenant,employee,interval.date()).orElse(null);if(c==null||!c.companyId().equals(company))continue;
    var seconds=measuring.measurableLeaveSeconds(tenant,company,employee,interval.date(),interval.start(),interval.end());
    if(seconds==null)throw new com.rpb.reservation.workforce.domain.WorkforceException("LEAVE_UNIT_POLICY_REQUIRED",409);
    var fraction=BigDecimal.valueOf(seconds).divide(c.dailyHours().multiply(BigDecimal.valueOf(3600)),12,RoundingMode.HALF_UP);
    result.merge(interval.date(),fraction,(a,b)->a.add(b).min(BigDecimal.ONE));
   }
  }
  return Collections.unmodifiableMap(result);
 }
}
