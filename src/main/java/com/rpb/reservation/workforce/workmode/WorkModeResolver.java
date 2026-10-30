package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import java.time.*;import java.util.*;import org.springframework.stereotype.Service;
/** Resolves immutable dated employee assignments; does not dynamically follow a renamed/changed position. */
@Service
public class WorkModeResolver {
 private final WorkModeRepository repository;
 public WorkModeResolver(WorkModeRepository repository){this.repository=repository;}
 public ClockPolicy resolve(UUID tenant,UUID employee,UUID store,LocalDate date){
  var a=repository.assignments(tenant,employee).stream().filter(p->!p.effectiveFrom().isAfter(date)).max(Comparator.comparing(Assignment::effectiveFrom)).orElse(null);
  if(a==null)return new ClockPolicy("legacy",null,0,null,null,0);
  var home=a.mode().equals("home")?repository.addresses(tenant,employee).stream().filter(h->h.status().equals("approved")&&!h.effectiveFrom().isAfter(date)).max(Comparator.comparing(HomeAddress::effectiveFrom)):Optional.<HomeAddress>empty();
  var site=a.mode().equals("onsite")?repository.site(tenant,store):null;
  return new ClockPolicy(a.mode(),a.id(),a.version(),site!=null?site.point():home.map(HomeAddress::point).orElse(null),home.map(HomeAddress::id).orElse(null),home.map(HomeAddress::version).orElse(0L),site==null?null:site.version());
 }
 public ClockPolicy snapshot(UUID tenant,UUID employee,UUID store,LocalDate date){var p=resolve(tenant,employee,store,date);return p.assignmentId()==null?null:p;}
 /** Captures only immutable dated assignments, without consulting mutable geometry. */
 public Map<UUID,List<Assignment>> assignmentsFor(UUID tenant,List<UUID> employees){return repository.assignmentsFor(tenant,employees);}
 public ClockPolicy approvalSnapshot(List<Assignment> assignments,LocalDate date){
  var a=assignments.stream().filter(p->!p.effectiveFrom().isAfter(date)).max(Comparator.comparing(Assignment::effectiveFrom)).orElse(null);
  return a==null?null:new ClockPolicy(a.mode(),a.id(),a.version(),null,null,0);
 }
 public List<Assignment> assignments(UUID tenant,UUID employee){return repository.assignments(tenant,employee);}
}
