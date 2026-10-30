package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.statutory.StatutoryCalculator;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;
public class LegacyPayrollCalculationService extends PayrollCalculationService {
 private final Clock clock;
 public LegacyPayrollCalculationService(Clock clock){super(clock);this.clock=clock;}
 public PayrollSnapshot calculate(PayrollSources.Capture capture,YearMonth month){
  var lines=new ArrayList<PayrollLine>();var issues=new LinkedHashSet<String>();
  BigDecimal gross=BigDecimal.ZERO,net=BigDecimal.ZERO,employeeCpf=BigDecimal.ZERO,employerCpf=BigDecimal.ZERO,sdl=BigDecimal.ZERO,extra=BigDecimal.ZERO;
  for(var source:capture.lines()){
   var line=line(source,month,capture.statutoryPolicy());lines.add(line);issues.addAll(line.issues());gross=gross.add(new BigDecimal(line.grossPay()));net=net.add(new BigDecimal(line.netPay()));employeeCpf=employeeCpf.add(new BigDecimal(line.employeeCpf()));employerCpf=employerCpf.add(new BigDecimal(line.employerCpf()));sdl=sdl.add(new BigDecimal(line.sdl()));extra=extra.add(new BigDecimal(line.reimbursements())).add(new BigDecimal(line.employerCharges()));
  }
  if(lines.isEmpty())issues.add("NO_EMPLOYEES");if(capture.statutoryPolicy()==null)issues.add("STATUTORY_POLICY_MISSING");var levy=sdl.setScale(0,capture.statutoryPolicy()==null?RoundingMode.FLOOR:com.rpb.reservation.workforce.statutory.StatutoryPolicyConfig.rounding(capture.statutoryPolicy().config().companySdlRounding()));
  return new PayrollSnapshot(capture.fingerprint(),capture.company().name(),month.toString(),capture.statutoryPolicy()==null?"UNCONFIGURED":capture.statutoryPolicy().id()+"/v"+capture.statutoryPolicy().version(),List.copyOf(lines),List.copyOf(issues),PayrollValues.money(gross),PayrollValues.money(net),PayrollValues.money(employeeCpf),PayrollValues.money(employerCpf),PayrollValues.money(levy),PayrollValues.money(gross.add(employerCpf).add(extra).add(levy)),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),capture.statutoryPolicy());
 }
 private PayrollLine line(PayrollSources.CapturedLine s,YearMonth month,com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy policy){
  var issues=new LinkedHashSet<>(s.issues());var p=s.profile();var input=s.input();
  long work=s.sources().stream().filter(SourceRef::payable).mapToLong(SourceRef::workSeconds).sum(),paid=s.sources().stream().filter(SourceRef::payable).mapToLong(SourceRef::paidLeaveSeconds).sum(),unpaid=s.sources().stream().filter(SourceRef::payable).mapToLong(SourceRef::unpaidLeaveSeconds).sum();
  if(p!=null&&!p.rule().basis().equals("hourly")&&(!input.unitsReviewed()||input.payableDays()==null||(p.rule().basis().equals("monthly")&&input.workingDays()==null)||(!PayrollGeneration.automatic(p.rule())&&input.note().isBlank())))issues.add("UNITS_REQUIRED");
  if(input.payableDays()!=null&&new BigDecimal(input.payableDays()).compareTo(BigDecimal.valueOf(month.lengthOfMonth()))>0)issues.add("UNITS_REQUIRED");
  if(s.specialDays()&&(!input.specialDaysReviewed()||input.note().isBlank()))issues.add("SPECIAL_DAYS_REVIEW");
  if((p==null||!PayrollGeneration.automatic(p.rule()))&&s.sources().stream().noneMatch(SourceRef::payable)&&(!input.unitsReviewed()||input.note().isBlank()))issues.add("NO_SOURCES_REVIEW");
  PayCalculator.Calculation c=null;com.rpb.reservation.workforce.statutory.StatutoryDtos.Result cpf=null;
  if(p!=null&&!issues.contains("UNITS_REQUIRED")){
   try{var r=p.rule();c=new PayCalculator().calculate(new PayCalculator.Rule(r.basis(),new BigDecimal(r.rate()),new BigDecimal(r.dayHours()),new BigDecimal(r.weekHours()),r.overtimeEnabled(),new BigDecimal(r.overtimeMultiplier())),s.sources().stream().map(x->new PayCalculator.WorkDay(x.workDate(),x.workSeconds(),x.paidLeaveSeconds(),x.payable())).toList(),new PayCalculator.Units(number(input.workingDays()),number(input.payableDays()),PayrollGeneration.automatic(r)),items(r.items()),items(input.items()));}
   catch(WorkforceException e){issues.add("NEGATIVE_NET");}
   if(policy==null)issues.add("STATUTORY_POLICY_MISSING");
   if(c!=null&&policy!=null)try{cpf=new StatutoryCalculator().calculate(month,p.cpf(),c.grossPay(),c.grossPay(),policy.config());}catch(WorkforceException e){issues.add(e.code().equals("WORKFORCE_CPF_PARTIAL_MONTH")?"CPF_PARTIAL_MONTH":e.code().equals("WORKFORCE_PAYROLL_UNSUPPORTED")?"UNSUPPORTED_PERIOD":"CPF_PROFILE_INVALID");}
  }
  var cdac=new CdacPayroll.Decision(BigDecimal.ZERO,null);
  if(c!=null&&p!=null)try{cdac=CdacPayroll.evaluate(month,p,input,policy,c.grossPay());}catch(WorkforceException e){issues.add(e.code().startsWith("CDAC_")?e.code():"CDAC_PROFILE_INVALID");}
  var components=new ArrayList<Component>();if(c!=null)c.components().forEach(x->components.add(new Component(x.label(),x.kind(),value(x.amount()))));
  if(cdac.calculation()!=null&&Set.of("automatic","override").contains(cdac.calculation().mode()))components.add(new Component("CDAC","cdac",value(cdac.deduction())));
  BigDecimal ee=cpf==null?BigDecimal.ZERO:cpf.employeeCpf(),er=cpf==null?BigDecimal.ZERO:cpf.employerCpf(),levy=cpf==null?BigDecimal.ZERO:cpf.sdl(),net=c==null?BigDecimal.ZERO:c.netBeforeCpf().subtract(ee).subtract(cdac.deduction());
  if(net.signum()<0)issues.add("NEGATIVE_NET");
  return new PayrollLine(s.employee().id(),s.employee().name(),s.employee().employeeNo(),p,input,work,c==null?0:c.normalSeconds(),c==null?0:c.overtimeSeconds(),paid,unpaid,value(c==null?null:c.basicPay()),value(c==null?null:c.overtimePay()),value(c==null?null:c.allowances()),value(c==null?null:c.reimbursements()),value(c==null?null:c.deductions().add(cdac.deduction())),value(c==null?null:c.grossPay()),value(ee),value(er),levy.toPlainString(),value(c==null?null:c.employerCharges()),value(net),cpf==null?"":cpf.rateCode(),List.copyOf(components),s.sources(),List.copyOf(issues),cdac.calculation());
 }
 private BigDecimal number(String s){return s==null?null:new BigDecimal(s);}
 private String value(BigDecimal n){return PayrollValues.money(n==null?BigDecimal.ZERO:n);}
 private List<PayCalculator.Item> items(List<Item> list){return list.stream().map(i->new PayCalculator.Item(i.label(),i.kind(),new BigDecimal(i.amount()),i.proration())).toList();}
}
