package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.positions.PositionRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class WorkModeService {
 private final WorkforceAccess gate;private final WorkforceEmployeeRepository employees;private final PositionRepository positions;private final WorkModeRepository repository;private final WorkModeResolver resolver;private final WorkforceAudit audit;private final Clock clock;
 public WorkModeService(WorkforceAccess gate,WorkforceEmployeeRepository employees,PositionRepository positions,WorkModeRepository repository,WorkModeResolver resolver,WorkforceAudit audit,Clock clock){this.gate=gate;this.employees=employees;this.positions=positions;this.repository=repository;this.resolver=resolver;this.audit=audit;this.clock=clock;}
 private LocalDate today(){return LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));}
 public WorkModePage page(CurrentActor actor,UUID store,UUID employee){var e=WorkModeAccess.manage(gate,employees,actor,store,employee);return page(actor,store,e,true);}
 public WorkModePage self(CurrentActor actor,UUID store){return page(actor,store,WorkModeAccess.self(gate,employees,actor,store),false);}
 private WorkModePage page(CurrentActor actor,UUID store,WorkforceDtos.Employee e,boolean manage){return new WorkModePage(e.id(),e.name(),manage,manage&&!actor.actorId().equals(e.accountId()),today(),repository.assignments(actor.tenantId(),e.id()),repository.addresses(actor.tenantId(),e.id()),resolver.resolve(actor.tenantId(),e.id(),store,today()));}
 @Transactional public Assignment save(CurrentActor actor,UUID store,UUID employee,UUID id,AssignmentWrite c){
  gate.sourceWrite(actor);WorkModeAccess.manage(gate,employees,actor,store,employee);if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.effectiveFrom()==null||c.inheritPosition()==null)throw WorkforceException.invalid();
  positions.lock(actor.tenantId());employees.lock(employee);var e=WorkModeAccess.manage(gate,employees,actor,store,employee);var old=repository.assignment(actor.tenantId(),id).orElse(null);boolean inherit=c.inheritPosition();
  if(old!=null){if(old.employeeId().equals(employee)&&old.effectiveFrom().equals(c.effectiveFrom())&&old.origin().equals(inherit?"position":"personal")&&(inherit||old.mode().equals(c.mode()))&&old.version()==c.expectedVersion()+1)return old;throw WorkforceException.conflict();}
  var all=repository.assignments(actor.tenantId(),employee);var latest=all.isEmpty()?null:all.getLast();if((latest==null?0:latest.version())!=c.expectedVersion())throw WorkforceException.conflict();
  if(c.effectiveFrom().isBefore(today().plusDays(1))||c.effectiveFrom().isBefore(e.hireDate())||(latest!=null&&!c.effectiveFrom().isAfter(latest.effectiveFrom())))throw WorkforceException.invalid();
  var position=e.positionId()==null?null:positions.find(actor.tenantId(),e.positionId()).orElse(null);String mode=inherit?(position==null||position.attendanceMode()==null?"legacy":position.attendanceMode()):WorkModeRules.mode(c.mode());
  var next=new Assignment(id,employee,c.effectiveFrom(),mode,inherit?"position":"personal",inherit&&position!=null?position.id():null,inherit&&position!=null?position.version():0,c.expectedVersion()+1);repository.insert(actor.tenantId(),next);audit.changed(actor,store,"work-mode.save",id,latest,next);return next;
 }
 /** Called inside employee save after the position/employee locks, copying the default once. */
 @Transactional public void adoptPosition(CurrentActor actor,UUID store,WorkforceDtos.Employee old,WorkforceDtos.Employee saved,LocalDate date){
  if(saved.positionId()==null||(old!=null&&Objects.equals(old.positionId(),saved.positionId())))return;
  var p=positions.find(actor.tenantId(),saved.positionId()).orElse(null);if(p==null||p.attendanceMode()==null)return;
  var all=repository.assignments(actor.tenantId(),saved.id());var latest=all.isEmpty()?null:all.getLast();if(latest!=null&&latest.origin().equals("personal"))return;
  var from=old==null?(saved.hireDate().isAfter(date)?saved.hireDate():date):date.plusDays(1);
  if(latest!=null&&!from.isAfter(latest.effectiveFrom()))throw WorkforceException.conflict();
  var next=new Assignment(UUID.randomUUID(),saved.id(),from,p.attendanceMode(),"position",p.id(),p.version(),latest==null?1:latest.version()+1);repository.insert(actor.tenantId(),next);audit.changed(actor,store,"work-mode.position",next.id(),latest,next);
 }
 public Site site(CurrentActor actor,UUID store){gate.enter(actor,store);return repository.site(actor.tenantId(),store);}
 @Transactional public Site saveSite(CurrentActor actor,UUID store,SiteWrite c){
  gate.sourceWrite(actor);gate.enter(actor,store);gate.manage(actor,store);if(c==null||c.expectedVersion()==null||c.expectedVersion()<0)throw WorkforceException.invalid();var point=WorkModeRules.point(c.point());repository.siteLock(actor.tenantId(),store);var old=repository.site(actor.tenantId(),store);
  if(point.equals(old.point())&&(old.version()==c.expectedVersion()||old.version()==c.expectedVersion()+1))return old;if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();var next=new Site(point,old.version()+1);repository.site(actor.tenantId(),store,next);audit.changed(actor,store,"clock-site.save",store,old,next);return next;
 }
}
