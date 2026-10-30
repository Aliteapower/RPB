package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Transactional(readOnly=true)
public class LegacyPayrollBatchService {
 private final WorkforceAccess gate;private final PayrollAccess access;private final PayrollRepository repository;private final LegacyPayrollSources sources;private final PayrollCalculationService calculator;private final WorkforceEmployeeRepository employees;private final WorkforceAudit audit;private final Clock clock;
 public LegacyPayrollBatchService(WorkforceAccess gate,PayrollAccess access,PayrollRepository repository,LegacyPayrollSources sources,PayrollCalculationService calculator,WorkforceEmployeeRepository employees,WorkforceAudit audit,Clock clock){this.gate=gate;this.access=access;this.repository=repository;this.sources=sources;this.calculator=calculator;this.employees=employees;this.audit=audit;this.clock=clock;}
 public List<BatchSummary> list(CurrentActor actor,UUID store,UUID company){access.enter(actor,store,company);var states=repository.list(actor.tenantId(),company,YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore"))).minusMonths(24).atDay(1));if(states.size()>100)throw WorkforceException.invalid();for(var b:states)access.enter(actor,store,company,PayrollViews.stores(b.calculation()));return states.stream().map(b->PayrollViews.summary(b,0)).toList();}
 @Transactional public BatchDetail create(CurrentActor actor,UUID store,UUID id,BatchWrite c){
  gate.enter(actor,store);if(id==null||c==null||c.companyId()==null)throw WorkforceException.invalid();var month=PayrollValues.month(c.month());access.enter(actor,store,c.companyId());repository.lock(actor.tenantId(),id);
  var old=repository.find(actor.tenantId(),id).orElse(null);if(old!=null){if(!old.company().equals(c.companyId())||!old.month().equals(month))throw WorkforceException.conflict();return detail(actor,store,id);}
  var company=repository.company(actor.tenantId(),c.companyId());if(!company.enabled())throw new WorkforceException("WORKFORCE_COMPANY_DISABLED",409);
  if(month.isAfter(YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore")))))throw WorkforceException.invalid();
  repository.lockSources(actor.tenantId());
  repository.create(actor.tenantId(),c.companyId(),id,company.name(),month,actor.actorId(),clock.instant());var saved=repository.find(actor.tenantId(),id).orElseThrow();
  var capture=sources.capture(actor.tenantId(),c.companyId(),month,List.of());access.enter(actor,store,c.companyId(),capture.stores());
  if(capture.lines().stream().anyMatch(l->l.profile()!=null&&PayrollGeneration.automatic(l.profile().rule()))){repository.update(saved,"calculated",calculator.calculate(capture,month),actor.actorId(),clock.instant());saved=repository.find(actor.tenantId(),id).orElseThrow();}
  audit.changed(actor,store,"payroll.create",id,null,PayrollViews.audit(saved));return view(actor,store,saved,capture);
 }
 public BatchDetail detail(CurrentActor actor,UUID store,UUID id){gate.enter(actor,store);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));return view(actor,store,b,null);}
 @Transactional public BatchDetail input(CurrentActor actor,UUID store,UUID id,UUID employee,InputWrite raw){
  gate.enter(actor,store);repository.lock(actor.tenantId(),id);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));if(!Set.of("draft","calculated").contains(b.status()))throw WorkforceException.conflict();var c=PayrollValues.input(raw);
  var capture=sources.capture(actor.tenantId(),b.company(),b.month(),repository.inputs(actor.tenantId(),id));access.enter(actor,store,b.company(),capture.stores());if(capture.lines().stream().noneMatch(l->l.employee().id().equals(employee)))throw WorkforceException.missing();
  var old=repository.inputs(actor.tenantId(),id).stream().filter(i->i.employeeId().equals(employee)).findFirst().orElse(PayrollSources.defaultInput(employee));
  var cdacReview=c.cdacReview()!=null?c.cdacReview():Boolean.TRUE.equals(c.cdacAutomatic())?null:old.cdacReview();
  var next=new LineInput(employee,old.version()+1,c.workingDays(),c.payableDays(),c.unitsReviewed(),c.specialDaysReviewed(),c.items(),c.note(),cdacReview);
  if(old.version()==c.expectedVersion()+1&&new LineInput(employee,old.version(),c.workingDays(),c.payableDays(),c.unitsReviewed(),c.specialDaysReviewed(),c.items(),c.note(),cdacReview).equals(old))return view(actor,store,b,capture);
  if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();repository.input(b,next);repository.update(b,"draft",null,actor.actorId(),clock.instant());var saved=repository.find(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"payroll.input",id,Map.of("employeeId",employee,"version",old.version()),Map.of("employeeId",employee,"version",next.version()));return view(actor,store,saved,null);
 }
 @Transactional public BatchDetail action(CurrentActor actor,UUID store,UUID id,UUID command,ActionWrite raw){
  gate.enter(actor,store);if(command==null||raw==null||raw.expectedVersion()==null||raw.expectedVersion()<1||!Set.of("calculate","confirm","publish","void").contains(raw.action()==null?"":raw.action()))throw WorkforceException.invalid();var c=new ActionWrite(raw.expectedVersion(),raw.action(),PayrollValues.text(raw.note(),500,true));
  repository.lock(actor.tenantId(),id);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));var replay=repository.replay(actor.tenantId(),id,command,actor.actorId(),c);if(replay.isPresent()){access.enter(actor,store,b.company(),PayrollViews.stores(replay.get().calculation()));return replay.get();}
  if(b.version()!=c.expectedVersion())throw WorkforceException.conflict();PayrollSnapshot calculation=b.calculation();String status;PayrollSources.Capture capture=null;
  switch(c.action()){
   case "calculate","confirm" -> {
    if(!Set.of("draft","calculated").contains(b.status())||(c.action().equals("confirm")&&!b.status().equals("calculated")))throw WorkforceException.conflict();
    repository.lockSources(actor.tenantId());access.enter(actor,store,b.company());capture=sources.capture(actor.tenantId(),b.company(),b.month(),repository.inputs(actor.tenantId(),id));access.enter(actor,store,b.company(),capture.stores());if(!capture.company().enabled())throw new WorkforceException("WORKFORCE_COMPANY_DISABLED",409);
    if(c.action().equals("calculate")){calculation=calculator.calculate(capture,b.month());status="calculated";}
    else{
     if(calculation==null||!calculation.sourceFingerprint().equals(capture.fingerprint()))throw new WorkforceException("WORKFORCE_PAYROLL_SOURCE_CHANGED",409);
     if(!calculation.issues().isEmpty()||!calculator.calculate(capture,b.month()).issues().isEmpty()||!LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore"))).isAfter(b.month().atEndOfMonth()))throw new WorkforceException("WORKFORCE_PAYROLL_BLOCKED",409);
     for(var line:capture.lines())if(actor.actorId().equals(line.employee().accountId()))throw WorkforceException.forbidden();status="confirmed";
    }
   }
   case "publish" -> {if(!b.status().equals("confirmed"))throw WorkforceException.conflict();status="published";}
   case "void" -> {if(!Set.of("draft","calculated","confirmed").contains(b.status())||!repository.payments(actor.tenantId(),id).isEmpty())throw WorkforceException.conflict();status="voided";}
   default -> throw WorkforceException.invalid();
  }
  repository.update(b,status,calculation,actor.actorId(),clock.instant());var saved=repository.find(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"payroll."+c.action(),id,PayrollViews.audit(b),PayrollViews.audit(saved));var response=view(actor,store,saved,capture);repository.command(actor.tenantId(),id,command,actor.actorId(),c,response);return response;
 }
 private BatchDetail view(CurrentActor actor,UUID store,PayrollRepository.State b,PayrollSources.Capture known){
  var inputs=repository.inputs(actor.tenantId(),b.id());var capture=known==null?sources.capture(actor.tenantId(),b.company(),b.month(),inputs):known;access.enter(actor,store,b.company(),capture.stores());
  boolean frozen=Set.of("confirmed","published","voided").contains(b.status())&&b.calculation()!=null;
  var people=frozen?PayrollViews.employees(b.calculation()):capture.lines().stream().map(l->new Option(l.employee().id(),l.employee().name()+" · "+l.employee().employeeNo())).toList();
  return new BatchDetail(PayrollViews.summary(b,people.size()),people,inputs,b.calculation(),b.calculation()!=null&&!b.calculation().sourceFingerprint().equals(capture.fingerprint()),repository.payments(actor.tenantId(),b.id()));
 }
}
