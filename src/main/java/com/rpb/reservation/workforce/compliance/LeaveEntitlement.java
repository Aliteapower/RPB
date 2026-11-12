package com.rpb.reservation.workforce.compliance;
import static com.rpb.reservation.workforce.compliance.ComplianceDtos.*;
import java.math.*;import java.time.*;import java.time.temporal.ChronoUnit;
import com.rpb.reservation.workforce.domain.WorkforceException;
/** Pure statutory quantities. No ledger mutation or payroll inference. */
public final class LeaveEntitlement {
 private LeaveEntitlement(){}
 public record Annual(String unit,BigDecimal annual,BigDecimal earned,int completedMonths,int serviceYear){}
 public record Sick(String unit,BigDecimal outpatient,BigDecimal hospitalTotal){public BigDecimal remainingHospital(BigDecimal usedOutpatient,BigDecimal usedHospital){return hospitalTotal.subtract(usedOutpatient).subtract(usedHospital).max(BigDecimal.ZERO);}}
 public static Annual annual(Rules r,LocalDate start,LocalDate on,BigDecimal weekly,BigDecimal comparableWeekly,BigDecimal comparableDaily){
  ComplianceRules.validate(r);if(start==null||on==null||on.isBefore(start)||!ComplianceRules.positive(comparableWeekly)||!ComplianceRules.positive(comparableDaily))throw WorkforceException.invalid();
  int months=Math.toIntExact(ChronoUnit.MONTHS.between(start,on)),year=months/12+1;
  var full=BigDecimal.valueOf(r.annualDays().get(Math.min(year-1,r.annualDays().size()-1)));boolean part=ComplianceRules.partTime(r,weekly);
  var annual=part?full.multiply(weekly).multiply(comparableDaily).divide(comparableWeekly,12,RoundingMode.HALF_UP):full;
  int periodMonths=months%12;var earned=months<r.eligibilityMonths()?BigDecimal.ZERO:annual.multiply(BigDecimal.valueOf(periodMonths)).divide(BigDecimal.valueOf(12),12,RoundingMode.HALF_UP);
  int scale=part?r.partTimePrecision():0;return new Annual(part?"hour":"day",annual.setScale(scale,RoundingMode.HALF_UP),earned.setScale(scale,RoundingMode.HALF_UP),months,year);
 }
 /** Calendar allocation preserves service-year rates and excludes approved unpaid service-day equivalents. */
 public static Annual calendarAnnual(Rules r,LocalDate start,LocalDate on,BigDecimal weekly,BigDecimal comparableWeekly,BigDecimal comparableDaily){return calendarAnnual(r,start,on,weekly,comparableWeekly,comparableDaily,java.util.Map.of());}
 public static Annual calendarAnnual(Rules r,LocalDate start,LocalDate on,BigDecimal weekly,BigDecimal comparableWeekly,BigDecimal comparableDaily,java.util.Map<LocalDate,BigDecimal> unpaidDays){
  var current=annual(r,start,on,weekly,comparableWeekly,comparableDaily);
  for(var e:unpaidDays.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue().signum()<0||e.getValue().compareTo(BigDecimal.ONE)>0)throw WorkforceException.invalid();
  boolean part=current.unit().equals("hour");var factor=part?weekly.multiply(comparableDaily).divide(comparableWeekly,12,RoundingMode.HALF_UP):BigDecimal.ONE;
  var boundary=LocalDate.of(on.getYear(),1,1).minusDays(1);var total=BigDecimal.ZERO;
  int first=Math.max(0,(int)ChronoUnit.YEARS.between(start,boundary)-1),last=Math.max(0,(int)ChronoUnit.YEARS.between(start,on));
  for(int y=first;y<=last;y++){
   var serviceStart=start.plusYears(y);if(serviceStart.isAfter(on))continue;var serviceEnd=start.plusYears(y+1).minusDays(1);
   var amount=BigDecimal.valueOf(r.annualDays().get(Math.min(y,r.annualDays().size()-1))).multiply(factor);
   total=total.add(serviceEarned(r,start,serviceStart,serviceEnd,on,amount,part,unpaidDays).subtract(serviceEarned(r,start,serviceStart,serviceEnd,boundary,amount,part,unpaidDays)));
  }
  return new Annual(current.unit(),current.annual(),total,current.completedMonths(),current.serviceYear());
 }
 private static BigDecimal serviceEarned(Rules r,LocalDate hire,LocalDate start,LocalDate end,LocalDate on,BigDecimal annual,boolean part,java.util.Map<LocalDate,BigDecimal> unpaid){
  if(on.isBefore(start)||on.isBefore(hire.plusMonths(r.eligibilityMonths())))return BigDecimal.ZERO;
  var limit=on.isAfter(end)?end:on;int completed=0;
  // Unpaid absence reduces the completed months of THIS service year. It never reappears as a late grant next year.
  for(int month=1;month<=12;month++){
   var base=start.plusMonths(month).atStartOfDay();var completion=base;
   for(int attempt=0;attempt<=unpaid.size()+1;attempt++){
    var date=completion.minusNanos(1).toLocalDate();var excluded=unpaid.entrySet().stream().filter(e->!e.getKey().isBefore(start)&&!e.getKey().isAfter(end)&&!e.getKey().isAfter(date)).map(java.util.Map.Entry::getValue).reduce(BigDecimal.ZERO,BigDecimal::add);
    var next=base.plusNanos(excluded.multiply(new BigDecimal("86400000000000")).setScale(0,RoundingMode.HALF_UP).longValueExact());if(next.equals(completion))break;completion=next;
   }
   if(!completion.minusNanos(1).toLocalDate().isAfter(limit))completed=month;
  }
  return annual.multiply(BigDecimal.valueOf(completed)).divide(BigDecimal.valueOf(12),part?r.partTimePrecision():0,RoundingMode.HALF_UP);
 }

 public static Sick sick(Rules r,int months,BigDecimal weekly,BigDecimal comparableWeekly,BigDecimal comparableDaily){
  ComplianceRules.validate(r);if(months<0||!ComplianceRules.positive(comparableWeekly)||!ComplianceRules.positive(comparableDaily))throw WorkforceException.invalid();boolean part=ComplianceRules.partTime(r,weekly);SickTier tier=null;for(var t:r.sickTiers())if(t.months()<=months)tier=t;
  var factor=part?weekly.multiply(comparableDaily).divide(comparableWeekly,12,RoundingMode.HALF_UP):BigDecimal.ONE;int scale=part?r.partTimePrecision():0;
  return new Sick(part?"hour":"day",factor.multiply(BigDecimal.valueOf(tier==null?0:tier.outpatient())).setScale(scale,RoundingMode.HALF_UP),factor.multiply(BigDecimal.valueOf(tier==null?0:tier.hospital())).setScale(scale,RoundingMode.HALF_UP));
 }
}
