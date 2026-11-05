package com.rpb.reservation.workforce.attendancepolicy;
import static com.rpb.reservation.workforce.attendancepolicy.AttendancePolicyDtos.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;import com.rpb.reservation.workforce.workmode.WorkModeRepository;import com.rpb.reservation.workforce.domain.WorkforceException;import java.time.*;import java.util.*;import org.springframework.stereotype.Service;
@Service public class AttendancePolicyResolver {
 private final AttendancePolicyRepository policies;private final WorkforceEmployeeRepository employees;private final WorkModeRepository workModes;
 public AttendancePolicyResolver(AttendancePolicyRepository policies,WorkforceEmployeeRepository employees,WorkModeRepository workModes){this.policies=policies;this.employees=employees;this.workModes=workModes;}
 public EffectiveAttendancePolicy resolveForEmployee(UUID tenant,UUID employee,LocalDate date){return resolve(tenant,policies.company(tenant,employee,date),employee,date);}
 public EffectiveAttendancePolicy resolve(UUID tenant,UUID company,UUID employee,LocalDate date){
  employees.find(tenant,employee).orElseThrow(WorkforceException::missing);if(!policies.company(tenant,employee,date).equals(company))throw WorkforceException.missing();
  var p=policies.assignments(tenant,employee).stream().filter(a->!a.effectiveFrom().isAfter(date)).max(Comparator.comparing(PolicyVersion::effectiveFrom)).orElse(null);
  if(p!=null){if(!p.companyId().equals(company))throw WorkforceException.conflict();return new EffectiveAttendancePolicy(p.mode(),p.id(),p.version(),p.sourceStoreId(),p.origin(),p.arrangement(),p.timezone(),"explicit_assignment");}
  var old=workModes.assignments(tenant,employee).stream().filter(a->!a.effectiveFrom().isAfter(date)).max(Comparator.comparing(com.rpb.reservation.workforce.workmode.WorkModeDtos.Assignment::effectiveFrom)).orElse(null);
  return new EffectiveAttendancePolicy(old!=null&&old.mode().equals("free")?"free":"clock",null,0,null,"work_mode",null,null,"current_work_mode_fact");
 }
 public List<ExpectedInterval> expected(UUID tenant,UUID company,UUID employee,LocalDate from,LocalDate to){
  if(from==null||to==null||to.isBefore(from)||to.isAfter(from.plusDays(366)))throw WorkforceException.invalid();var employeeFact=employees.find(tenant,employee).orElseThrow(WorkforceException::missing);var result=new ArrayList<ExpectedInterval>();
  for(var date=from;!date.isAfter(to);date=date.plusDays(1)){if(date.isBefore(employeeFact.hireDate())||(employeeFact.leaveDate()!=null&&date.isAfter(employeeFact.leaveDate())))continue;var p=resolve(tenant,company,employee,date);var a=p.arrangement();if(a==null||!Set.of("clock","default_normal").contains(p.mode())||!a.weekdays().contains(date.getDayOfWeek().getValue()))continue;var start=date.atTime(a.startTime()).atZone(ZoneId.of(p.timezone())).toInstant();result.add(new ExpectedInterval(date,start,start.plusSeconds((a.standardWorkMinutes()+a.unpaidBreakMinutes())*60L),a.standardWorkMinutes()*60L,a.unpaidBreakMinutes()*60L,a.unpaidBreakMinutes()==0?null:start.plusSeconds(a.unpaidBreakStartOffsetMinutes()*60L),a.unpaidBreakMinutes()==0?null:start.plusSeconds((a.unpaidBreakStartOffsetMinutes()+a.unpaidBreakMinutes())*60L),p.policyId(),p.version()));}return List.copyOf(result);
 }
}
