package com.rpb.reservation.workforce.compliance;
import static com.rpb.reservation.workforce.compliance.ComplianceDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;import java.util.*;
public final class ComplianceRules {
 private ComplianceRules(){}
 public static void validate(Rules r){
  if(r==null||!"sg-leave-hours-v1".equals(r.model())||r.annualDays()==null||r.annualDays().isEmpty()||r.annualDays().size()>60||r.eligibilityMonths()<0||r.eligibilityMonths()>12||r.sickTiers()==null||r.sickTiers().isEmpty()||r.sickTiers().size()>12||!positive(r.partTimeBelowHours())||!positive(r.normalWeekHours())||!positive(r.workmanThreshold())||!positive(r.nonWorkmanThreshold())||!positive(r.overtimeMinimum())||r.overtimeMinimum().compareTo(BigDecimal.ONE)<0||r.carryMonths()<1||r.carryMonths()>60||r.partTimePrecision()<0||r.partTimePrecision()>6||r.maxDailyHours()<1||r.maxDailyHours()>24||r.maxMonthlyOvertimeHours()<1||r.maxMonthlyOvertimeHours()>744||r.maxContinuousHours()<1||r.maxContinuousHours()>24||r.minimumMealMinutes()<1||r.minimumMealMinutes()>240||!positive(r.shortWeekDailyHours())||!positive(r.longWeekDailyHours())||r.shortWeekMaxDays()<1||r.shortWeekMaxDays()>7||r.companyCarryMonths()<0||r.companyCarryMonths()>60)throw WorkforceException.invalid();
  int last=-1;for(var d:r.annualDays()){if(d==null||d<last||d>366)throw WorkforceException.invalid();last=d;}
  int month=-1,out=-1,total=-1;for(var t:r.sickTiers()){if(t==null||t.months()<=month||t.months()>120||t.outpatient()<out||t.hospital()<total||t.outpatient()<0||t.hospital()<t.outpatient()||t.hospital()>366)throw WorkforceException.invalid();month=t.months();out=t.outpatient();total=t.hospital();}
 }
 public static boolean positive(BigDecimal n){return n!=null&&n.signum()>0&&n.scale()<=6;}
 public static boolean partTime(Rules r,BigDecimal weekly){validate(r);if(!positive(weekly))throw WorkforceException.invalid();return weekly.compareTo(r.partTimeBelowHours())<0;}
 public static boolean partFour(Rules r,String category,BigDecimal monthlyBasic){validate(r);if(monthlyBasic==null||monthlyBasic.signum()<0||!Set.of("workman","non_workman","manager").contains(category))throw WorkforceException.invalid();return !category.equals("manager")&&monthlyBasic.compareTo(category.equals("workman")?r.workmanThreshold():r.nonWorkmanThreshold())<=0;}
 public static BigDecimal monthlyBasic(String basis,BigDecimal rate,BigDecimal dailyHours,BigDecimal weeklyHours,BigDecimal weeklyDays){return switch(basis){case "monthly"->rate;case "daily"->rate.multiply(weeklyDays).multiply(new BigDecimal("52")).divide(new BigDecimal("12"),12,java.math.RoundingMode.HALF_UP);case "hourly"->rate.multiply(weeklyHours).multiply(new BigDecimal("52")).divide(new BigDecimal("12"),12,java.math.RoundingMode.HALF_UP);default->throw WorkforceException.invalid();};}
}
