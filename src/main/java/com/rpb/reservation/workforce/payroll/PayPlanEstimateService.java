package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayPlanEstimateDtos.*;import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;import com.rpb.reservation.workforce.domain.WorkforceException;import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.*;import java.time.*;import java.util.*;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
/** Hypothetical complete-month ordinary wages. No persisted inputs, approvals, snapshots or payments. */
@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class PayPlanEstimateService {
 private final PayrollAccess access;private final PayrollConfigurationRepository configs;private final WorkforceEmployeeRepository employees;private final PayrollCalculationService calculator;
 public PayPlanEstimateService(PayrollAccess access,PayrollConfigurationRepository configs,WorkforceEmployeeRepository employees,PayrollCalculationService calculator){this.access=access;this.configs=configs;this.employees=employees;this.calculator=calculator;}
 public Result estimate(CurrentActor actor,UUID store,Request w){
  if(w==null)throw WorkforceException.invalid();access.enter(actor,store,w.companyId());access.employee(actor,w.companyId(),w.employeeId());
  var month=PayrollValues.month(w.month());var rule=PayrollRules.normalize(w.rule());var e=employees.find(actor.tenantId(),w.employeeId()).orElseThrow(WorkforceException::missing);
  if(w.days()==null||w.days()<1||w.days()>month.lengthOfMonth())throw WorkforceException.invalid();var day=PayrollValues.decimal(w.dayHours());if(day.signum()<=0||day.compareTo(new BigDecimal("24"))>0)throw WorkforceException.invalid();
  long seconds;try{seconds=day.multiply(new BigDecimal("3600")).longValueExact();}catch(ArithmeticException ex){throw WorkforceException.invalid();}
  var cpf=w.cpf();if(cpf==null)throw WorkforceException.invalid();if(e.birthDate()!=null)cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile(cpf.citizenship(),e.birthDate(),cpf.prDate(),cpf.prMode(),cpf.electionReference(),cpf.prDocumentId());
  if(w.cdac()==null)throw new WorkforceException("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED",409);
  var cdac=CdacPayroll.profile(w.cdac(),cpf,rule);var policy=configs.activePolicy(actor.tenantId(),month);if(policy==null)throw new WorkforceException("STATUTORY_POLICY_MISSING",409);
  // No overtime, actual attendance, or one-off events are claimed by this estimate.
  var items=rule.items().stream().map(i->new Item(i.label(),i.kind(),i.amount(),i.proration(),i.itemId(),null)).toList();
  var assumed=new Rule(rule.basis(),rule.rate(),rule.dayHours(),rule.weekHours(),false,rule.overtimeMultiplier(),items);
  var p=new PayProfile(UUID.randomUUID(),w.companyId(),e.id(),e.name(),month.atDay(1),UUID.randomUUID(),1,assumed,cpf,1,null,cdac);
  var refs=new ArrayList<SourceRef>();for(int d=1;d<=w.days();d++)refs.add(new SourceRef(UUID.randomUUID(),store,month.atDay(d),1,"estimate",seconds,0,0,true));
  var input=new LineInput(e.id(),0,w.days().toString(),w.days().toString(),true,true,List.of(),"Full-month full attendance assumption");
  var line=new PayrollSources.CapturedLine(e,p,input,refs,List.of(),false,null,null,"eligible");
  var snapshot=calculator.calculate(new PayrollSources.Capture("estimate",new PayrollRepository.CompanyInfo("Estimate","",1,true,0),List.of(line),Set.of(store),policy),month);
  if(!snapshot.issues().isEmpty())throw new WorkforceException(snapshot.issues().getFirst(),409);
  var b=PayBreakdown.of(snapshot.lines().getFirst());var publicItems=b.items().stream().map(i->new Component(i.label(),i.kind(),i.amount())).toList();
  var result=new PayBreakdown.Values(b.basic(),b.overtime(),b.additional(),b.allowance(),b.accommodation(),b.reimbursements(),b.otherDeductions(),b.cdac(),b.employeeCpf(),b.employerCpf(),b.sdl(),b.employerCharges(),b.gross(),b.net(),b.employerCost(),publicItems);
  return new Result(month.toString(),w.days(),day.stripTrailingZeros().toPlainString(),seconds*w.days(),result,policy.name()+" / v"+policy.version(),List.of("FULL_MONTH","FULL_ATTENDANCE_ASSUMED","NO_OVERTIME_OR_ONE_OFF_ADJUSTMENTS","INDIVIDUAL_COMPANY_COST_ESTIMATE","DRAFT_NOT_EFFECTIVE_PAYROLL"));
 }
}
