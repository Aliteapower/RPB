package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class HomeAddressService {
 private final WorkforceAccess gate;private final WorkforceEmployeeRepository employees;private final WorkModeRepository repository;private final WorkforceAudit audit;private final Clock clock;
 public HomeAddressService(WorkforceAccess gate,WorkforceEmployeeRepository employees,WorkModeRepository repository,WorkModeResolver resolver,WorkforceAudit audit,Clock clock){this.gate=gate;this.employees=employees;this.repository=repository;this.audit=audit;this.clock=clock;}
 private LocalDate tomorrow(){return LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore"))).plusDays(1);}
 @Transactional public HomeAddress submit(CurrentActor actor,UUID store,UUID id,HomeWrite c){
  gate.sourceWrite(actor);var e=WorkModeAccess.self(gate,employees,actor,store);if(id==null||c==null||c.effectiveFrom()==null)throw WorkforceException.invalid();var point=WorkModeRules.point(c.point());employees.lock(e.id());e=WorkModeAccess.self(gate,employees,actor,store);var old=repository.address(actor.tenantId(),id).orElse(null);
  if(old!=null){if(old.employeeId().equals(e.id())&&old.applicantId().equals(actor.actorId())&&old.point().equals(point)&&old.effectiveFrom().equals(c.effectiveFrom()))return old;throw WorkforceException.conflict();}
  if(c.effectiveFrom().isBefore(tomorrow())||c.effectiveFrom().isBefore(e.hireDate())||!e.enabled()||!e.status().equals("active"))throw WorkforceException.invalid();
  var h=new HomeAddress(id,e.id(),point,c.effectiveFrom(),"pending",1,actor.actorId(),null,"",clock.instant());repository.insertHome(actor.tenantId(),h);var saved=repository.address(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"home-address.submit",id,null,saved);return saved;
 }
 @Transactional public HomeAddress decide(CurrentActor actor,UUID store,UUID id,HomeDecision raw){
  gate.sourceWrite(actor);gate.enter(actor,store);if(raw==null||raw.expectedVersion()==null||raw.note()==null||raw.note().isBlank()||raw.note().trim().length()>500||!Set.of("approved","rejected").contains(raw.status()==null?"":raw.status()))throw WorkforceException.invalid();var c=new HomeDecision(raw.expectedVersion(),raw.status(),raw.note().trim());
  var initial=repository.address(actor.tenantId(),id).orElseThrow(WorkforceException::missing);WorkModeAccess.manage(gate,employees,actor,store,initial.employeeId());employees.lock(initial.employeeId());var e=WorkModeAccess.manage(gate,employees,actor,store,initial.employeeId());var old=repository.address(actor.tenantId(),id).orElseThrow();
  if(actor.actorId().equals(old.applicantId())||actor.actorId().equals(e.accountId()))throw WorkforceException.forbidden();
  if(old.version()==c.expectedVersion()+1&&old.status().equals(c.status())&&actor.actorId().equals(old.reviewerId())&&old.reviewNote().equals(c.note()))return old;
  if(old.version()!=c.expectedVersion()||!old.status().equals("pending"))throw WorkforceException.conflict();
  if(c.status().equals("approved")&&old.effectiveFrom().isBefore(tomorrow()))throw WorkforceException.invalid();repository.decide(actor.tenantId(),id,actor.actorId(),c);var next=repository.address(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"home-address."+c.status(),id,old,next);return next;
 }
}
