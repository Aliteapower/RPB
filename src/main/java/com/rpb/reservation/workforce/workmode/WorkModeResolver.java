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
  var modes=WorkModeOptions.modes(a.mode(),a.allowedModes());
  var home=modes.contains("home")?repository.addresses(tenant,employee).stream().filter(h->h.status().equals("approved")&&!h.effectiveFrom().isAfter(date)).max(Comparator.comparing(HomeAddress::effectiveFrom)):Optional.<HomeAddress>empty();
  var site=modes.contains("onsite")?repository.site(tenant,store):null;
  var options=modes.stream().map(mode->new ClockOption(mode,mode.equals("onsite")?site.point():mode.equals("home")?home.map(HomeAddress::point).orElse(null):null,mode.equals("home")?home.map(HomeAddress::id).orElse(null):null,mode.equals("home")?home.map(HomeAddress::version).orElse(0L):0,mode.equals("onsite")?site.version():null)).toList();
  var selected=options.stream().filter(o->o.mode().equals(a.mode())).findFirst().orElseThrow();
  return new ClockPolicy(a.mode(),a.id(),a.version(),selected.point(),selected.addressId(),selected.addressVersion(),selected.siteVersion(),a.allowedModes()==null?null:options);
 }
 public ClockPolicy snapshot(UUID tenant,UUID employee,UUID store,LocalDate date){var p=resolve(tenant,employee,store,date);return p.assignmentId()==null?null:p;}
 /** Captures only immutable dated assignments, without consulting mutable geometry. */
 public Map<UUID,List<Assignment>> assignmentsFor(UUID tenant,List<UUID> employees){return repository.assignmentsFor(tenant,employees);}
 public ClockPolicy approvalSnapshot(List<Assignment> assignments,LocalDate date){
  var a=assignments.stream().filter(p->!p.effectiveFrom().isAfter(date)).max(Comparator.comparing(Assignment::effectiveFrom)).orElse(null);
  return a==null?null:new ClockPolicy(a.mode(),a.id(),a.version(),null,null,0,null,a.allowedModes()==null?null:WorkModeOptions.modes(a.mode(),a.allowedModes()).stream().map(mode->new ClockOption(mode,null,null,0,null)).toList());
 }
 public List<Assignment> assignments(UUID tenant,UUID employee){return repository.assignments(tenant,employee);}
}
