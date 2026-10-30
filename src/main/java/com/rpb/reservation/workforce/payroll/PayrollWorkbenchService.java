package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollWorkbenchDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;import java.time.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
/** Orchestrates existing sources, calculation validation, changes and ledger in one consistent read. */
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PayrollWorkbenchService {
 private final PayrollAccess access;private final PayrollRepository repository;private final PayrollSources sources;private final PayrollCalculationService calculator;private final PayrollProgressService progress;private final Clock clock;
 public PayrollWorkbenchService(PayrollAccess access,PayrollRepository repository,PayrollSources sources,PayrollCalculationService calculator,PayrollProgressService progress,Clock clock){this.access=access;this.repository=repository;this.sources=sources;this.calculator=calculator;this.progress=progress;this.clock=clock;}
 public Workbench read(CurrentActor actor,UUID store,UUID company,String month,Long version){return read(actor,store,company,month,version,null);}
 public Workbench read(CurrentActor actor,UUID store,UUID company,String value,Long version,UUID batchId){
  access.enter(actor,store,company);var month=PayrollValues.month(value);
  var batch=batchId==null?repository.forMonth(actor.tenantId(),company,month).orElse(null):repository.find(actor.tenantId(),batchId).orElseThrow(WorkforceException::missing);
  if(batch!=null&&(!batch.company().equals(company)||!batch.month().equals(month)))throw WorkforceException.missing();
  if(version!=null&&(batch==null||version!=batch.version()))throw WorkforceException.conflict();
  if(batch!=null)access.enter(actor,store,company,PayrollViews.stores(batch.calculation()));
  var computation=batch==null?null:repository.computation(actor.tenantId(),batch.id()).orElse(null);var cache=computation==null?null:computation.cache();
  if(cache!=null)access.enter(actor,store,company,PayrollViews.stores(cache.snapshot()));
  var capture=sources.capture(actor.tenantId(),company,month,batch==null?List.of():repository.inputs(actor.tenantId(),batch.id()));
  access.enter(actor,store,company,capture.stores());
  // Reuse the actual calculator's validation; this preview is not saved or returned as a wage baseline.
  var validation=calculator.calculate(capture,month);var missing=new ArrayList<Missing>();var approvals=new ArrayList<Approval>();int exempt=0;
  for(var line:capture.lines()){
   var id=line.employee().id();String name=line.employee().name();
   if(line.profile()!=null&&PayrollGeneration.fixed(line.profile().rule()))exempt++;
   var codes=validation.lines().stream().filter(l->l.employeeId().equals(id)).findFirst().orElseThrow().issues();
   for(String code:codes)if(!Set.of("APPROVAL_MISSING","APPROVAL_STALE","SOURCE_PENDING").contains(code))missing.add(new Missing(id,name,category(code),code,line.profile()==null?null:line.profile().id(),action(code)));
   for(var ref:line.sources())if(ref.evidence()!=null&&!"current".equals(ref.evidence().approvalState()))approvals.add(new Approval(id,name,ref.scheduleId(),ref.storeId(),ref.workDate(),ref.evidence().approvalState(),!ref.payable()));
  }
  for(String code:validation.issues())if(missing.stream().noneMatch(m->m.code().equals(code))&&Set.of("NO_EMPLOYEES","STATUTORY_POLICY_MISSING").contains(code))missing.add(new Missing(null,"",category(code),code,null,action(code)));
  var report=batch==null?null:progress.changes(actor,store,batch.id());
  var baseline=batch!=null&&batch.calculation()!=null?batch.calculation():cache==null?null:cache.snapshot();
  boolean changed=baseline!=null&&!Objects.equals(baseline.sourceFingerprint(),capture.fingerprint());
  var reasons=PayrollSourceReasons.compare(baseline,capture,cache,changed);
  boolean frozen=batch!=null&&Set.of("confirmed","published","voided").contains(batch.status());
  boolean trustedAmounts=batch!=null&&Set.of("confirmed","published").contains(batch.status());
  return new Workbench(company,month.toString(),batch==null?null:batch.id(),batch==null?null:batch.version(),batch==null?null:batch.status(),clock.instant(),baseline==null?null:baseline.calculatedAt(),frozen,(int)missing.stream().map(Missing::employeeId).filter(Objects::nonNull).distinct().count(),List.copyOf(missing),(int)approvals.stream().map(Approval::employeeId).distinct().count(),approvals.size(),(int)approvals.stream().filter(Approval::context).count(),exempt,List.copyOf(approvals),reasons,report,PayrollPaymentProjection.summarize(batch==null?null:batch.calculation(),batch==null?List.of():repository.payments(actor.tenantId(),batch.id()),trustedAmounts));
 }
 private static String category(String code){if(code.startsWith("PROFILE_"))return "profile";if(code.startsWith("CPF_")||code.startsWith("CDAC_")||code.startsWith("STATUTORY_"))return "statutory";if(code.startsWith("WORK_MODE"))return "workmode";if(Set.of("UNITS_REQUIRED","SPECIAL_DAYS_REVIEW","NEGATIVE_NET","NO_SOURCES_REVIEW").contains(code))return "input";return "source";}
 private static String action(String code){return switch(category(code)){case "profile"->"profile";case "statutory"->code.startsWith("STATUTORY_")?"policy":"profile";case "workmode"->"employee";case "input"->"input";default->"review";};}
}
