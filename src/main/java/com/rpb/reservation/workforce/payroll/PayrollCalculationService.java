package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.statutory.StatutoryCalculator;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class PayrollCalculationService {
 private final Clock clock;
 private final PayrollComputeExecutor compute;
 public PayrollCalculationService(Clock clock){this.clock=clock;this.compute=null;}
 @org.springframework.beans.factory.annotation.Autowired
 public PayrollCalculationService(Clock clock,PayrollComputeExecutor compute){this.clock=clock;this.compute=java.util.Objects.requireNonNull(compute);}
 public PayrollSnapshot calculate(PayrollSources.Capture capture,YearMonth month){
  var calculated=compute==null?capture.lines().stream().map(source->line(source,month,capture.statutoryPolicy())).toList():compute.orderedMap(capture.lines(),source->line(source,month,capture.statutoryPolicy()));
  return aggregate(capture,month,calculated);
 }
 /** Rebuild all company totals, including company-level SDL rounding, in the supplied current order. */
 public PayrollSnapshot aggregate(PayrollSources.Capture capture,YearMonth month,List<PayrollLine> calculated){
  var lines=new ArrayList<PayrollLine>();var issues=new LinkedHashSet<String>();
  BigDecimal gross=BigDecimal.ZERO,net=BigDecimal.ZERO,employeeCpf=BigDecimal.ZERO,employerCpf=BigDecimal.ZERO,sdl=BigDecimal.ZERO,extra=BigDecimal.ZERO;
  for(var line:calculated){
   lines.add(line);issues.addAll(line.issues());gross=add(gross,line.grossPay());net=add(net,line.netPay());employeeCpf=add(employeeCpf,line.employeeCpf());employerCpf=add(employerCpf,line.employerCpf());sdl=add(sdl,line.sdl());extra=extra.add(new BigDecimal(line.reimbursements())).add(new BigDecimal(line.employerCharges()));
  }
  if(lines.isEmpty())issues.add("NO_EMPLOYEES");if(capture.statutoryPolicy()==null)issues.add("STATUTORY_POLICY_MISSING");var levy=sdl==null?null:sdl.setScale(0,capture.statutoryPolicy()==null?RoundingMode.FLOOR:com.rpb.reservation.workforce.statutory.StatutoryPolicyConfig.rounding(capture.statutoryPolicy().config().companySdlRounding()));if(sdl!=null&&capture.segmentSdl()!=null){levy=sdl.add(new BigDecimal(capture.segmentSdl().individual())).setScale(0,capture.statutoryPolicy()==null?RoundingMode.FLOOR:com.rpb.reservation.workforce.statutory.StatutoryPolicyConfig.rounding(capture.statutoryPolicy().config().companySdlRounding())).subtract(new BigDecimal(capture.segmentSdl().recognised()));if(levy.signum()<0||!capture.segmentSdl().policyCurrent())issues.add("SEGMENT_COMPANY_SDL_REVIEW");}
  return new PayrollSnapshot(capture.fingerprint(),capture.company().name(),month.toString(),capture.statutoryPolicy()==null?"UNCONFIGURED":capture.statutoryPolicy().id()+"/v"+capture.statutoryPolicy().version(),List.copyOf(lines),List.copyOf(issues),nullable(gross),nullable(net),nullable(employeeCpf),nullable(employerCpf),nullable(levy),gross==null||employerCpf==null||levy==null?null:nullable(gross.add(employerCpf).add(extra).add(levy)),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),capture.statutoryPolicy());
 }
 private PayrollLine line(PayrollSources.CapturedLine s,YearMonth month,com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy policy){
  var issues=new LinkedHashSet<>(s.issues());issues.addAll(PayrollLineage.issues(s));var p=s.profile();var input=s.input();if(FullAttendanceBonus.configured(p)&&!Set.of("eligible","ineligible","deferred").contains(Objects.toString(s.attendanceBonusStatus(),"")))issues.add("ATTENDANCE_BONUS_REVIEW_REQUIRED");
  long work=s.sources().stream().filter(SourceRef::payable).mapToLong(SourceRef::workSeconds).sum(),paid=s.sources().stream().filter(SourceRef::payable).mapToLong(SourceRef::paidLeaveSeconds).sum(),unpaid=s.sources().stream().filter(SourceRef::payable).mapToLong(SourceRef::unpaidLeaveSeconds).sum();
  if(p!=null&&!p.rule().basis().equals("hourly")&&(!input.unitsReviewed()||input.payableDays()==null||(p.rule().basis().equals("monthly")&&input.workingDays()==null)||(!PayrollGeneration.automatic(p.rule())&&input.note().isBlank())))issues.add("UNITS_REQUIRED");
  if(input.payableDays()!=null&&new BigDecimal(input.payableDays()).compareTo(BigDecimal.valueOf(month.lengthOfMonth()))>0)issues.add("UNITS_REQUIRED");
  if(s.specialDays()&&(!input.specialDaysReviewed()||input.note().isBlank()))issues.add("SPECIAL_DAYS_REVIEW");
  if((p==null||!PayrollGeneration.automatic(p.rule()))&&s.sources().stream().noneMatch(SourceRef::payable)&&(!input.unitsReviewed()||input.note().isBlank()))issues.add("NO_SOURCES_REVIEW");
  boolean openingConflict=issues.contains(com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository.OPENING_PAYROLL_CONFLICT);
  PayCalculator.Calculation c=null;com.rpb.reservation.workforce.statutory.StatutoryDtos.Result cpf=null;AwCalculation aw=null;boolean awUnknown=openingConflict;
  if(p!=null&&!issues.contains("UNITS_REQUIRED")){
   try{var r=p.rule();c=new PayCalculator().calculate(new PayCalculator.Rule(r.basis(),new BigDecimal(r.rate()),new BigDecimal(r.dayHours()),new BigDecimal(r.weekHours()),r.overtimeEnabled(),new BigDecimal(r.overtimeMultiplier())),s.sources().stream().map(x->new PayCalculator.WorkDay(x.workDate(),x.workSeconds(),x.paidLeaveSeconds(),x.payable())).toList(),new PayCalculator.Units(number(input.workingDays()),number(input.payableDays()),PayrollGeneration.automatic(r)),items(r.items(),s.attendanceBonusStatus()),items(input.items(),null));}
   catch(WorkforceException e){issues.add("NEGATIVE_NET");}
   if(policy==null)issues.add("STATUTORY_POLICY_MISSING");
   if(c!=null&&policy!=null)try{if(openingConflict)throw new WorkforceException(com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository.OPENING_PAYROLL_CONFLICT,409);var decision=PayrollAw.calculate(s,month,c.grossPay(),policy);cpf=decision.cpf();aw=decision.trace();}catch(WorkforceException e){boolean event=input.items().stream().anyMatch(i->i.eventEvidence()!=null);String issue=event||openingConflict?e.code():e.code().equals("WORKFORCE_CPF_PARTIAL_MONTH")?"CPF_PARTIAL_MONTH":e.code().equals("WORKFORCE_PAYROLL_UNSUPPORTED")?"UNSUPPORTED_PERIOD":"CPF_PROFILE_INVALID";issues.add(issue);if(event||openingConflict){awUnknown=true;if(event)aw=PayrollAw.missing(s,c.grossPay(),issue);}}
   if(c!=null&&policy==null&&input.items().stream().anyMatch(i->i.eventEvidence()!=null)){awUnknown=true;aw=PayrollAw.missing(s,c.grossPay(),"STATUTORY_POLICY_MISSING");}
  }
  var cdac=new CdacPayroll.Decision(BigDecimal.ZERO,null);
  if(c!=null&&p!=null)try{cdac=CdacPayroll.evaluate(month,p,input,policy,c.grossPay());}catch(WorkforceException e){issues.add(e.code().startsWith("CDAC_")?e.code():"CDAC_PROFILE_INVALID");}
  var components=new ArrayList<Component>(PayrollLineage.components(s,month,c));if(c!=null)components.addAll(PayrollLineage.statutory(month,policy,cpf,value(c.grossPay())));
  if(cdac.calculation()!=null&&Set.of("automatic","override").contains(cdac.calculation().mode()))components.add(PayrollLineage.statutory("CDAC","cdac",value(cdac.deduction()),policy.id().toString(),policy.version(),month,"cdac_v2",value(c.grossPay()),cdac.calculation().standardAmount()));
  BigDecimal ee=cpf==null?BigDecimal.ZERO:cpf.employeeCpf(),er=cpf==null?BigDecimal.ZERO:cpf.employerCpf(),levy=cpf==null?BigDecimal.ZERO:cpf.sdl(),net=c==null?BigDecimal.ZERO:c.netBeforeCpf().subtract(ee).subtract(cdac.deduction());
  if(awUnknown&&policy!=null&&c!=null)levy=c.grossPay().signum()==0?BigDecimal.ZERO:c.grossPay().multiply(new BigDecimal(policy.config().sdl().rate())).max(new BigDecimal(policy.config().sdl().minimum())).min(new BigDecimal(policy.config().sdl().maximum()));
  if(net.signum()<0&&!awUnknown)issues.add("NEGATIVE_NET");
  return new PayrollLine(s.employee().id(),s.employee().name(),s.employee().employeeNo(),p,input,work,c==null?0:c.normalSeconds(),c==null?0:c.overtimeSeconds(),paid,unpaid,value(c==null?null:c.basicPay()),value(c==null?null:c.overtimePay()),value(c==null?null:c.allowances()),value(c==null?null:c.reimbursements()),value(c==null?null:c.deductions().add(cdac.deduction())),value(c==null?null:c.grossPay()),awUnknown?null:value(ee),awUnknown?null:value(er),awUnknown&&policy==null?null:levy.toPlainString(),value(c==null?null:c.employerCharges()),awUnknown?null:value(net),cpf==null?"":cpf.rateCode(),List.copyOf(components),s.sources(),List.copyOf(issues),cdac.calculation(),PayrollLineage.hours(s,c),s.governance(),aw);
 }
 private BigDecimal add(BigDecimal total,String value){return total==null||value==null?null:total.add(new BigDecimal(value));}
 private String nullable(BigDecimal value){return value==null?null:PayrollValues.money(value);}
 private BigDecimal number(String s){return s==null?null:new BigDecimal(s);}
 private String value(BigDecimal n){return PayrollValues.money(n==null?BigDecimal.ZERO:n);}
 private List<PayCalculator.Item> items(List<Item> list,String attendanceStatus){return list.stream().map(i->new PayCalculator.Item(i.label(),i.kind(),new BigDecimal(i.kind().equals("ow_attendance")&&!"eligible".equals(attendanceStatus)?"0":i.amount()),i.proration())).toList();}
}
