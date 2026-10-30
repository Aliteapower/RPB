package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
@Service
@Transactional(readOnly=true)
public class PayrollBatchService {
 private final WorkforceAccess gate;private final PayrollAccess access;private final PayrollRepository repository;private final PayrollSources sources;private final PayrollCalculationService calculator;private final WorkforceEmployeeRepository employees;private final WorkforceAudit audit;private final Clock clock;
 private final PayrollTransactions transactions;
 private final PayrollIncremental incremental;
 public PayrollBatchService(WorkforceAccess gate,PayrollAccess access,PayrollRepository repository,PayrollSources sources,PayrollCalculationService calculator,WorkforceEmployeeRepository employees,WorkforceAudit audit,Clock clock,PlatformTransactionManager manager){this.gate=gate;this.access=access;this.repository=repository;this.sources=sources;this.calculator=calculator;this.employees=employees;this.audit=audit;this.clock=clock;this.transactions=new PayrollTransactions(manager);this.incremental=new PayrollIncremental(calculator);}
 public List<BatchSummary> list(CurrentActor actor,UUID store,UUID company){access.enter(actor,store,company);var states=repository.list(actor.tenantId(),company,YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore"))).minusMonths(24).atDay(1));if(states.size()>100)throw WorkforceException.invalid();for(var b:states)access.enter(actor,store,company,PayrollViews.stores(b.calculation()));return states.stream().map(b->PayrollViews.summary(b,0)).toList();}
 private record Creation(BatchDetail existing,PayrollSources.Capture capture){}
 @Transactional(propagation=Propagation.NOT_SUPPORTED) public BatchDetail create(CurrentActor actor,UUID store,UUID id,BatchWrite c){
  if(id==null||c==null||c.companyId()==null)throw WorkforceException.invalid();var month=PayrollValues.month(c.month());
  var prepared=transactions.read(()->{
   gate.enter(actor,store);access.enter(actor,store,c.companyId());
   var old=existingCreation(actor,store,id,c,month);if(old!=null)return new Creation(old,null);
   if(month.isAfter(YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore")))))throw WorkforceException.invalid();
   var capture=capture(actor,store,c.companyId(),month,List.of());return new Creation(null,capture);
  });
  if(prepared.existing()!=null)return prepared.existing();
  var result=prepared.capture().lines().stream().anyMatch(l->l.profile()!=null&&PayrollGeneration.automatic(l.profile().rule()))?incremental.calculate(prepared.capture(),month,null,true):null;
  var calculation=result==null?null:result.snapshot();
  return transactions.write(()->{
   gate.enter(actor,store);repository.lock(actor.tenantId(),id);access.enter(actor,store,c.companyId());
   var old=existingCreation(actor,store,id,c,month);if(old!=null)return old;
   repository.lockSources(actor.tenantId());var fresh=capture(actor,store,c.companyId(),month,List.of());unchanged(prepared.capture(),fresh);
   repository.create(actor.tenantId(),c.companyId(),id,fresh.company().name(),month,actor.actorId(),clock.instant());var saved=repository.find(actor.tenantId(),id).orElseThrow();
   if(calculation!=null){repository.update(saved,"calculated",calculation,actor.actorId(),clock.instant());saved=repository.find(actor.tenantId(),id).orElseThrow();recordComputation(saved,null,null,result,fresh);}
   audit.changed(actor,store,"payroll.create",id,null,PayrollViews.audit(saved));return view(actor,store,saved,fresh);
  });
 }
 private BatchDetail existingCreation(CurrentActor actor,UUID store,UUID id,BatchWrite c,YearMonth month){
  var old=repository.find(actor.tenantId(),id).orElse(null);if(old==null)return null;
  if(!old.company().equals(c.companyId())||!old.month().equals(month))throw WorkforceException.conflict();return detail(actor,store,id);
 }
 private PayrollSources.Capture capture(CurrentActor actor,UUID store,UUID company,YearMonth month,List<LineInput> inputs){
  var capture=sources.capture(actor.tenantId(),company,month,inputs);access.enter(actor,store,company,capture.stores());
  if(!capture.company().enabled())throw new WorkforceException("WORKFORCE_COMPANY_DISABLED",409);return capture;
 }
 private void unchanged(PayrollSources.Capture before,PayrollSources.Capture after){if(!before.fingerprint().equals(after.fingerprint()))throw new WorkforceException("WORKFORCE_PAYROLL_SOURCE_CHANGED",409);}
 public BatchDetail detail(CurrentActor actor,UUID store,UUID id){gate.enter(actor,store);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));return view(actor,store,b,null);}
 @Transactional public BatchDetail input(CurrentActor actor,UUID store,UUID id,UUID employee,InputWrite raw){
  gate.enter(actor,store);repository.lock(actor.tenantId(),id);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));if(!Set.of("draft","calculated").contains(b.status()))throw WorkforceException.conflict();var c=PayrollValues.input(raw);
  var capture=sources.capture(actor.tenantId(),b.company(),b.month(),repository.inputs(actor.tenantId(),id));access.enter(actor,store,b.company(),capture.stores());if(capture.lines().stream().noneMatch(l->l.employee().id().equals(employee)))throw WorkforceException.missing();
  var old=repository.inputs(actor.tenantId(),id).stream().filter(i->i.employeeId().equals(employee)).findFirst().orElse(PayrollSources.defaultInput(employee));
  var cdacReview=c.cdacReview()!=null?c.cdacReview():Boolean.TRUE.equals(c.cdacAutomatic())?null:old.cdacReview();
  var next=new LineInput(employee,old.version()+1,c.workingDays(),c.payableDays(),c.unitsReviewed(),c.specialDaysReviewed(),c.items(),c.note(),cdacReview);
  if(old.version()==c.expectedVersion()+1&&new LineInput(employee,old.version(),c.workingDays(),c.payableDays(),c.unitsReviewed(),c.specialDaysReviewed(),c.items(),c.note(),cdacReview).equals(old))return view(actor,store,b,capture);
  if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();repository.input(b,next);repository.update(b,"draft",null,actor.actorId(),clock.instant());var saved=repository.find(actor.tenantId(),id).orElseThrow();audit.changed(actor,store,"payroll.input",id,Map.of("employeeId",employee,"version",old.version()),Map.of("employeeId",employee,"version",next.version()));return view(actor,store,saved,capture);
 }
 private record ActionPreparation(PayrollRepository.State batch,PayrollSources.Capture capture,BatchDetail replay,PayrollComputation.State computation){}
 @Transactional(propagation=Propagation.NOT_SUPPORTED) public BatchDetail action(CurrentActor actor,UUID store,UUID id,UUID command,ActionWrite raw){
  if(command==null||raw==null||raw.expectedVersion()==null||raw.expectedVersion()<1||!Set.of("calculate","calculate_full","confirm","publish","void").contains(raw.action()==null?"":raw.action()))throw WorkforceException.invalid();
  var c=new ActionWrite(raw.expectedVersion(),raw.action(),PayrollValues.text(raw.note(),500,true));
  if(!Set.of("calculate","calculate_full","confirm").contains(c.action()))return transactions.write(()->simpleAction(actor,store,id,command,c));
  var prepared=transactions.read(()->{
   var b=authorized(actor,store,id);var replay=replay(actor,store,b,command,c);if(replay!=null)return new ActionPreparation(b,null,replay,null);
   calculable(b,c);var capture=capture(actor,store,b.company(),b.month(),repository.inputs(actor.tenantId(),id));
   return new ActionPreparation(b,capture,null,repository.computation(actor.tenantId(),id).orElse(null));
  });
  if(prepared.replay()!=null)return prepared.replay();
  var result=c.action().equals("confirm")?null:incremental.calculate(prepared.capture(),prepared.batch().month(),prepared.computation()==null?null:prepared.computation().cache(),c.action().equals("calculate_full"));
  var computed=result==null?calculator.calculate(prepared.capture(),prepared.batch().month()):result.snapshot();
  return transactions.write(()->{
   gate.enter(actor,store);repository.lock(actor.tenantId(),id);var b=authorized(actor,store,id);var replay=replay(actor,store,b,command,c);if(replay!=null)return replay;
   calculable(b,c);repository.lockSources(actor.tenantId());
   var fresh=capture(actor,store,b.company(),b.month(),repository.inputs(actor.tenantId(),id));unchanged(prepared.capture(),fresh);
   var calculation=c.action().equals("confirm")?b.calculation():computed;
   if(c.action().equals("confirm"))confirmable(actor,b,fresh,computed);
   var response=save(actor,store,b,command,c,c.action().equals("confirm")?"confirmed":"calculated",calculation,fresh);
   if(result!=null){var saved=repository.find(actor.tenantId(),id).orElseThrow();recordComputation(saved,b,prepared.computation(),result,fresh);}return response;
  });
 }
 private PayrollRepository.State authorized(CurrentActor actor,UUID store,UUID id){
  gate.enter(actor,store);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));return b;
 }
 private BatchDetail replay(CurrentActor actor,UUID store,PayrollRepository.State b,UUID command,ActionWrite c){
  var replay=repository.replay(actor.tenantId(),b.id(),command,actor.actorId(),c).orElse(null);if(replay!=null)access.enter(actor,store,b.company(),PayrollViews.stores(replay.calculation()));return replay;
 }
 private void calculable(PayrollRepository.State b,ActionWrite c){
  if(b.version()!=c.expectedVersion()||!Set.of("draft","calculated").contains(b.status())||(c.action().equals("confirm")&&!b.status().equals("calculated")))throw WorkforceException.conflict();
 }
 private void confirmable(CurrentActor actor,PayrollRepository.State b,PayrollSources.Capture capture,PayrollSnapshot computed){
  var calculation=b.calculation();if(calculation==null||!calculation.sourceFingerprint().equals(capture.fingerprint()))throw new WorkforceException("WORKFORCE_PAYROLL_SOURCE_CHANGED",409);
  if(!calculation.issues().isEmpty()||!computed.issues().isEmpty()||!LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore"))).isAfter(b.month().atEndOfMonth()))throw new WorkforceException("WORKFORCE_PAYROLL_BLOCKED",409);
  if(!PayrollIncremental.sameResult(calculation,computed))throw new WorkforceException("WORKFORCE_PAYROLL_SOURCE_CHANGED",409);
  for(var line:capture.lines())if(actor.actorId().equals(line.employee().accountId()))throw WorkforceException.forbidden();
 }
 private void recordComputation(PayrollRepository.State saved,PayrollRepository.State before,PayrollComputation.State previous,PayrollIncremental.Result result,PayrollSources.Capture capture){
  var baseline=previous!=null&&previous.cache()!=null?previous.cache().snapshot():before==null?null:before.calculation();
  Long baselineVersion=previous!=null?Long.valueOf(previous.batchVersion()):before==null?null:Long.valueOf(before.version());
  var comparison=PayrollChanges.compare(baseline,baselineVersion,result.snapshot());
  var stores=new TreeSet<UUID>(capture.stores());stores.addAll(PayrollViews.stores(baseline));stores.addAll(PayrollViews.stores(result.snapshot()));
  if(previous!=null&&previous.comparisonStores()!=null)stores.addAll(previous.comparisonStores());
  repository.computation(saved,new PayrollComputation.State(result.cache(),comparison,result.recalculated(),result.reused(),saved.version(),Collections.unmodifiableSet(stores)));
 }
 private BatchDetail simpleAction(CurrentActor actor,UUID store,UUID id,UUID command,ActionWrite raw){
  gate.enter(actor,store);if(command==null||raw==null||raw.expectedVersion()==null||raw.expectedVersion()<1||!Set.of("calculate","confirm","publish","void").contains(raw.action()==null?"":raw.action()))throw WorkforceException.invalid();var c=new ActionWrite(raw.expectedVersion(),raw.action(),PayrollValues.text(raw.note(),500,true));
  repository.lock(actor.tenantId(),id);var b=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);access.enter(actor,store,b.company(),PayrollViews.stores(b.calculation()));var replay=repository.replay(actor.tenantId(),id,command,actor.actorId(),c);if(replay.isPresent()){access.enter(actor,store,b.company(),PayrollViews.stores(replay.get().calculation()));return replay.get();}
  if(b.version()!=c.expectedVersion())throw WorkforceException.conflict();PayrollSnapshot calculation=b.calculation();String status;PayrollSources.Capture capture=null;
  switch(c.action()){
   case "publish" -> {if(!b.status().equals("confirmed"))throw WorkforceException.conflict();status="published";}
   case "void" -> {if(!Set.of("draft","calculated","confirmed").contains(b.status())||!repository.payments(actor.tenantId(),id).isEmpty())throw WorkforceException.conflict();status="voided";}
   default -> throw WorkforceException.invalid();
  }
  return save(actor,store,b,command,c,status,calculation,capture);
 }
 private BatchDetail save(CurrentActor actor,UUID store,PayrollRepository.State b,UUID command,ActionWrite c,String status,PayrollSnapshot calculation,PayrollSources.Capture capture){
  repository.update(b,status,calculation,actor.actorId(),clock.instant());var saved=repository.find(actor.tenantId(),b.id()).orElseThrow();audit.changed(actor,store,"payroll."+c.action(),b.id(),PayrollViews.audit(b),PayrollViews.audit(saved));var response=view(actor,store,saved,capture);repository.command(actor.tenantId(),b.id(),command,actor.actorId(),c,response);return response;
 }
 private BatchDetail view(CurrentActor actor,UUID store,PayrollRepository.State b,PayrollSources.Capture known){
  var inputs=repository.inputs(actor.tenantId(),b.id());var capture=known==null?sources.capture(actor.tenantId(),b.company(),b.month(),inputs):known;access.enter(actor,store,b.company(),capture.stores());
  boolean frozen=Set.of("confirmed","published","voided").contains(b.status())&&b.calculation()!=null;
  var people=frozen?PayrollViews.employees(b.calculation()):capture.lines().stream().map(l->new Option(l.employee().id(),l.employee().name()+" · "+l.employee().employeeNo())).toList();
  return new BatchDetail(PayrollViews.summary(b,people.size()),people,inputs,b.calculation(),b.calculation()!=null&&!b.calculation().sourceFingerprint().equals(capture.fingerprint()),repository.payments(actor.tenantId(),b.id()));
 }
}
