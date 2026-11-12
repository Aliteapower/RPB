package com.rpb.reservation.workforce.statutory.annual;

import static com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.*;
import static com.rpb.reservation.workforce.statutory.annual.AnnualWageCalculator.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.persistence.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public class AnnualWageService {
 private final WorkforceAccess gate;private final PayrollAccess access;private final WorkforceDirectory directory;private final AnnualWageRepository repository;private final StatutoryPolicyRepository policies;private final WorkforceEmployeeRepository employees;private final WorkforceAudit audit;private final Clock clock;
 public AnnualWageService(WorkforceAccess gate,PayrollAccess access,WorkforceDirectory directory,AnnualWageRepository repository,StatutoryPolicyRepository policies,WorkforceEmployeeRepository employees,WorkforceAudit audit,Clock clock){this.gate=gate;this.access=access;this.directory=directory;this.repository=repository;this.policies=policies;this.employees=employees;this.audit=audit;this.clock=clock;}
 private void enter(CurrentActor actor,UUID store,UUID company,UUID employee,int year){if(year<2000||year>2100)throw WorkforceException.invalid();access.enter(actor,store,company);access.employee(actor,company,employee);}
 private void writeAccess(CurrentActor actor){gate.headOffice(actor);for(var s:directory.stores(actor.tenantId()))gate.enter(actor,s.id());}
 public Page read(CurrentActor actor,UUID store,UUID company,UUID employee,int year){enter(actor,store,company,employee,year);var c=repository.capture(actor.tenantId(),company,employee,year);boolean writable=gate.isHeadOffice(actor)&&directory.stores(actor.tenantId()).stream().allMatch(s->actor.canAccessStore(s.id()));return new Page(new Context(c.companyId(),c.employeeId(),c.year(),c.version(),c.fingerprint(),c.months(),c.issues(),writable,writable?repository.saved(actor.tenantId(),company,employee,year).write().months():List.of()),new AnnualWageCalculator().assess(c),repository.reconciliations(actor.tenantId(),company,employee,year));}
 @Transactional public Page save(CurrentActor actor,UUID store,UUID company,UUID employee,int year,Write c){
  enter(actor,store,company,employee,year);writeAccess(actor);if(c==null||c.commandId()==null||c.expectedVersion()==null||c.expectedVersion()<0||c.months()==null||c.months().size()>12||c.reason()==null||c.reason().isBlank()||c.reason().length()>1000)throw WorkforceException.invalid();
  gate.sourceWrite(actor);repository.commandLock(c.commandId());repository.lock(actor.tenantId(),company,employee,year);var replay=repository.replay(actor,store,company,employee,year,c.commandId(),"opening",c,Page.class);if(replay.isPresent())return replay.get();var old=repository.saved(actor.tenantId(),company,employee,year);if(old.version()!=c.expectedVersion())throw WorkforceException.conflict();
  var seen=new HashSet<String>();var ids=new HashSet<UUID>();var now=YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore")));
  for(var f:c.months()){
   if(f==null||!seen.add(f.month())||!Set.of("actual","known-zero").contains(Objects.toString(f.state(),""))||f.evidenceReference()==null||f.evidenceReference().isBlank()||f.evidenceReference().length()>1000||f.events()==null||f.events().size()>100||f.cpf()==null||f.policyId()==null||f.policyVersion()==null)throw WorkforceException.invalid();
   var month=PayrollProfileValidation.month(f.month());if(month.getYear()!=year||!month.isBefore(now)||repository.internal(actor.tenantId(),company,employee,month))throw new WorkforceException("ANNUAL_INTERNAL_FACT_PROTECTED",409);
   var policy=policies.versions(actor.tenantId(),f.policyId()).stream().filter(p->p.version()==f.policyVersion()).findFirst().orElseThrow(WorkforceException::missing);if(policy.year()!=year||policy.effectiveFrom().isAfter(month.atDay(1))||policy.effectiveTo().isBefore(month.atEndOfMonth()))throw WorkforceException.invalid();
   PayrollProfileValidation.cpf(f.cpf(),null,clock);var ow=amount(f.rawOW());if(f.recognisedEmployeeCpf()!=null)amount(f.recognisedEmployeeCpf());if(f.recognisedEmployerCpf()!=null)amount(f.recognisedEmployerCpf());new StatutoryCalculator().calculate(month,f.cpf(),ow,ow,policy.config());
   if(f.state().equals("known-zero")&&(ow.signum()!=0||!f.events().isEmpty()||f.recognisedEmployeeCpf()!=null&&amount(f.recognisedEmployeeCpf()).signum()!=0||f.recognisedEmployerCpf()!=null&&amount(f.recognisedEmployerCpf()).signum()!=0))throw WorkforceException.invalid();
   for(var event:f.events()){if(event==null||event.evidence()==null)throw WorkforceException.invalid();WageEvents.validate(event.evidence(),"wage_event");if(!event.evidence().contributionMonth().equals(f.month())||!ids.add(event.evidence().eventId())||event.source()==null||event.source().isBlank())throw WorkforceException.invalid();amount(event.amount());}
   var declaredOw=f.events().stream().filter(e->e.evidence().classification().equals("OW")).map(e->amount(e.amount())).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);if(ow.compareTo(declaredOw)<0)throw WorkforceException.invalid();
  }
  if(!seen.containsAll(old.write().months().stream().map(OpeningMonth::month).toList())||!ids.containsAll(old.write().months().stream().flatMap(m->m.events().stream()).map(e->e.evidence().eventId()).toList()))throw new WorkforceException("ANNUAL_EVENT_HISTORY_IMMUTABLE",409);
  repository.registerOpening(actor.tenantId(),company,employee,year,c.months());repository.save(actor,company,employee,year,c);var response=read(actor,store,company,employee,year);repository.command(actor,store,company,employee,year,c.commandId(),"opening",c,response);audit.changed(actor,store,"statutory-year.opening",employee,old,response.context());return response;
 }
 @Transactional public Reconciliation reconcile(CurrentActor actor,UUID store,UUID company,UUID employee,int year,ReconcileWrite c){
  enter(actor,store,company,employee,year);writeAccess(actor);if(c==null||c.commandId()==null||c.expectedFingerprint()==null)throw WorkforceException.invalid();gate.sourceWrite(actor);repository.commandLock(c.commandId());repository.lock(actor.tenantId(),company,employee,year);var replay=repository.replay(actor,store,company,employee,year,c.commandId(),"reconcile",c,Reconciliation.class);if(replay.isPresent())return replay.get();
  var context=repository.capture(actor.tenantId(),company,employee,year);if(!context.fingerprint().equals(c.expectedFingerprint()))throw new WorkforceException("AW_CONTEXT_STALE",409);var settlement=PayrollProfileValidation.month(c.settlementMonth());var person=employees.find(actor.tenantId(),employee).orElseThrow(WorkforceException::missing);var due=YearMonth.of(year,12);if(person.leaveDate()!=null&&person.leaveDate().getYear()==year&&YearMonth.from(person.leaveDate()).isBefore(due))due=YearMonth.from(person.leaveDate());
  if(settlement.isBefore(due)||settlement.isAfter(YearMonth.now(clock.withZone(ZoneId.of("Asia/Singapore"))))||context.months().stream().anyMatch(f->!Set.of("actual","known-zero").contains(f.state())))throw new WorkforceException("ANNUAL_WAGES_INCOMPLETE",409);
  var assessment=new AnnualWageCalculator().assess(context);if(!assessment.issues().isEmpty())throw new WorkforceException(assessment.issues().getFirst(),409);
  var existing=repository.reconciliations(actor.tenantId(),company,employee,year).stream().filter(r->r.assessment().fingerprint().equals(context.fingerprint())&&r.settlementMonth().equals(c.settlementMonth())).findFirst();var result=existing.orElseGet(()->new Reconciliation(UUID.randomUUID(),company,employee,year,c.settlementMonth(),assessment,actor.actorId(),clock.instant()));if(existing.isEmpty())repository.reconciliation(actor.tenantId(),result);repository.command(actor,store,company,employee,year,c.commandId(),"reconcile",c,result);audit.changed(actor,store,"statutory-year.reconcile",result.id(),null,result);return result;
 }
}
