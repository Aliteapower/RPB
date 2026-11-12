package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;
import java.time.*;
import java.util.*;

/** Pure monthly unit generation. Never copies prior-month inputs or treats missing facts as attendance. */
public final class PayrollGeneration {
 private PayrollGeneration(){}
 public record Result(LineInput input,List<String> issues){}
 public static String mode(Rule rule){return rule.generation()==null?"manual":rule.generation().mode();}
 public static boolean automatic(Rule rule){return !mode(rule).equals("manual");}
 public static boolean fixed(Rule rule){return mode(rule).equals("fixed");}
 public static boolean sameOvertimeThresholds(Rule a,Rule b){return a.overtimeEnabled()==b.overtimeEnabled()&&new BigDecimal(a.dayHours()).compareTo(new BigDecimal(b.dayHours()))==0&&new BigDecimal(a.weekHours()).compareTo(new BigDecimal(b.weekHours()))==0;}
 public static boolean contract(Rule rule){return mode(rule).equals("contract");}
 public static Generation validate(Rule rule){
  var g=rule.generation();if(g==null)return null;
  if(g.mode()==null||!Set.of("manual","attendance","fixed","contract").contains(g.mode())||g.workingWeekdays()==null)throw WorkforceException.invalid();
  var days=g.workingWeekdays();if(days.size()>7||days.stream().anyMatch(d->d==null||d<1||d>7)||new HashSet<>(days).size()!=days.size())throw WorkforceException.invalid();
  if(g.mode().equals("fixed")&&(!rule.basis().equals("monthly")||rule.overtimeEnabled()))throw WorkforceException.invalid();
  if(g.mode().equals("attendance")&&rule.basis().equals("monthly")&&days.isEmpty())throw WorkforceException.invalid();
  if(contract(rule)){
   var terms=ContractPayCalculator.validate(g.contractTerms());
   if(rule.overtimeEnabled()||days.isEmpty()&&!(rule.basis().equals("monthly")&&terms.employmentProration().equals("full")&&terms.unpaidLeaveTreatment().equals("no_deduction")))throw WorkforceException.invalid();
  }else if(g.contractTerms()!=null)throw WorkforceException.invalid();
  return new Generation(g.mode(),days.stream().sorted().toList(),g.contractTerms());
 }
 public static String proration(Item item){
  String value=item.proration();if(value!=null&&(!Set.of("full","payable").contains(value)||!PayItemKinds.INCOME.contains(item.kind())))throw WorkforceException.invalid();return value;
 }
 public static Result generate(Rule rule,LineInput input,List<SourceRef> refs,YearMonth month,LocalDate from,LocalDate to){
  if(!automatic(rule))return new Result(input,List.of());validate(rule);
  var issues=new LinkedHashSet<String>();String working=null,payable=null;
  if(from==null||to==null||from.isAfter(to))issues.add("AUTO_UNITS_INVALID");
  else if(fixed(rule)){working="1";payable="1";}
  else if(contract(rule)){
   try{var c=contractUnits(rule,refs,month,from,to);working=c.workingDays().toPlainString();payable=c.payableDays().toPlainString();}
   catch(WorkforceException e){issues.add("CONTRACT_UNITS_INVALID");}
  }
  else if(!rule.basis().equals("hourly")){
   var byDate=new TreeMap<LocalDate,Long>();
   for(var ref:refs)if(ref.payable()&&YearMonth.from(ref.workDate()).equals(month)){
    if(ref.workDate().isBefore(from)||ref.workDate().isAfter(to)){issues.add("AUTO_UNITS_INVALID");continue;}
    byDate.merge(ref.workDate(),Math.addExact(ref.workSeconds(),ref.paidLeaveSeconds()),Math::addExact);
   }
   var expected=new TreeSet<LocalDate>();
   if(rule.basis().equals("monthly")){
    var weekdays=new HashSet<>(rule.generation().workingWeekdays());
    var calendar=month.atDay(1).datesUntil(month.plusMonths(1).atDay(1)).filter(d->weekdays.contains(d.getDayOfWeek().getValue())).toList();working=Integer.toString(calendar.size());
    calendar.stream().filter(d->!d.isBefore(from)&&!d.isAfter(to)).forEach(expected::add);
    if(!byDate.keySet().containsAll(expected))issues.add("AUTO_SCHEDULE_MISSING");
    if(byDate.keySet().stream().anyMatch(d->!expected.contains(d)))issues.add("AUTO_UNEXPECTED_DAY");
   }else expected.addAll(byDate.keySet());
   BigDecimal seconds=BigDecimal.ZERO,daySeconds=new BigDecimal(rule.dayHours()).multiply(BigDecimal.valueOf(3600));
   for(var date:expected)seconds=seconds.add(BigDecimal.valueOf(byDate.getOrDefault(date,0L)).min(daySeconds));
   // Divide once across all dates; money is rounded only by PayCalculator.
   payable=seconds.divide(daySeconds,12,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
   if(working!=null&&working.equals("0"))issues.add("AUTO_UNITS_INVALID");
   if(byDate.isEmpty())issues.add("AUTO_SCHEDULE_MISSING");
  }else if(refs.stream().noneMatch(SourceRef::payable))issues.add("AUTO_SCHEDULE_MISSING");
  // The generation marker and rule snapshot explain automatic units. Never invent a human review note.
  return new Result(new LineInput(input.employeeId(),input.version(),working,payable,true,input.specialDaysReviewed(),input.items(),input.note(),input.cdacReview(),mode(rule)),List.copyOf(issues));
 }
 public static ContractPayCalculator.Result contractUnits(Rule rule,List<SourceRef> refs,YearMonth month,LocalDate from,LocalDate to){
  return ContractPayCalculator.calculate(new PayCalculator.Rule(rule.basis(),new BigDecimal(rule.rate()),new BigDecimal(rule.dayHours()),new BigDecimal(rule.weekHours()),rule.overtimeEnabled(),new BigDecimal(rule.overtimeMultiplier())),rule.generation().contractTerms(),month,from,to,rule.generation().workingWeekdays(),refs.stream().filter(SourceRef::payable).filter(r->!r.workDate().isBefore(from)&&!r.workDate().isAfter(to)).map(r->new ContractPayCalculator.Day(r.workDate(),r.unpaidLeaveSeconds(),r.evidence()==null?0:r.evidence().extraSeconds())).toList());
 }
}
