package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.Session;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;import org.springframework.stereotype.Service;
@Service
public class WorkModeClock {
 private final WorkModeRepository repository;private final WorkModeResolver resolver;private final Clock clock;private final com.rpb.reservation.workforce.attendancepolicy.AttendancePolicyResolver attendancePolicies;
 public WorkModeClock(WorkModeRepository repository,WorkModeResolver resolver,Clock clock){this(repository,resolver,clock,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public WorkModeClock(WorkModeRepository repository,WorkModeResolver resolver,Clock clock,com.rpb.reservation.workforce.attendancepolicy.AttendancePolicyResolver attendancePolicies){this.repository=repository;this.resolver=resolver;this.clock=clock;this.attendancePolicies=attendancePolicies;}
 public ClockPolicy policy(UUID tenant,UUID employee,UUID store,LocalDate date,Session session){return session==null?resolver.snapshot(tenant,employee,store,date):session.clockPolicy();}
 /** Without a punch, mutable site/address geometry is not an attendance fact or an approval dependency. */
 public ClockPolicy approvalPolicy(UUID tenant,UUID employee,UUID store,LocalDate date,Session session){var p=policy(tenant,employee,store,date,session);return session!=null||p==null?p:WorkModeOptions.geometryFree(p);}
 public Map<UUID,ClockPolicy> approvalPolicies(UUID tenant,List<com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule> schedules,Map<UUID,Session> sessions){
  var employees=schedules.stream().filter(s->!sessions.containsKey(s.id())).map(s->s.employeeId()).distinct().toList();var assignments=resolver.assignmentsFor(tenant,employees);var result=new HashMap<UUID,ClockPolicy>();
  for(var s:schedules){var session=sessions.get(s.id());result.put(s.id(),session==null?resolver.approvalSnapshot(assignments.getOrDefault(s.employeeId(),List.of()),s.workDate()):session.clockPolicy());}return result;
 }
 public void verify(ClockPolicy p,Location location){
  WorkModeRules.location(location);if(p==null)return;
  if(p.mode().equals("free"))throw new WorkforceException("WORKFORCE_CLOCK_EXEMPT",409);
  if(Set.of("onsite","home").contains(p.mode())){if(p.point()==null)throw new WorkforceException(p.mode().equals("home")?"WORKFORCE_HOME_ADDRESS_REQUIRED":"WORKFORCE_CLOCK_SITE_REQUIRED",409);WorkModeRules.locate(p.point(),location,clock.instant());}
 }
 public void evidence(UUID tenant,UUID event,ClockPolicy p,Location l){repository.evidence(tenant,event,p,l);}
 public boolean exempt(UUID tenant,UUID employee,UUID store,LocalDate date,Session session){if(attendancePolicies!=null&&!attendancePolicies.resolveForEmployee(tenant,employee,date).mode().equals("clock"))return true;var p=policy(tenant,employee,store,date,session);return p!=null&&p.mode().equals("free");}
 public ClockPolicy current(UUID tenant,UUID employee,UUID store,LocalDate date){if(attendancePolicies!=null){var p=attendancePolicies.resolveForEmployee(tenant,employee,date);if(!p.mode().equals("clock"))return new ClockPolicy("free",p.policyId(),p.version(),null,null,0);}return resolver.resolve(tenant,employee,store,date);}
}
