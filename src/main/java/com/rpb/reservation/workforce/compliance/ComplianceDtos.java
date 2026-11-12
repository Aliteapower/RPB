package com.rpb.reservation.workforce.compliance;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public final class ComplianceDtos {
 private ComplianceDtos(){}
 public record Source(String code,String title,String url,String summary,LocalDate checkedOn){}
 public record SickTier(int months,int outpatient,int hospital){}
 public record Rules(String model,List<Integer> annualDays,int eligibilityMonths,List<SickTier> sickTiers,BigDecimal partTimeBelowHours,BigDecimal normalWeekHours,BigDecimal workmanThreshold,BigDecimal nonWorkmanThreshold,BigDecimal overtimeMinimum,int carryMonths,int partTimePrecision,int maxDailyHours,int maxMonthlyOvertimeHours,int maxContinuousHours,int minimumMealMinutes,BigDecimal shortWeekDailyHours,BigDecimal longWeekDailyHours,int shortWeekMaxDays,int companyCarryMonths,boolean allowContractualEncashment,boolean allowCompanyExtraEncashment){}
 public record Reference(String name,Rules rules,List<Source> sources){}
 public record Policy(UUID id,long version,String name,LocalDate effectiveFrom,LocalDate effectiveTo,String status,Rules rules,List<Source> sources,UUID reviewerId){}
 public record PolicyWrite(Long expectedVersion,String name,LocalDate effectiveFrom,LocalDate effectiveTo,Boolean publish,Rules rules,List<Source> sources){}
 public record Contract(UUID id,UUID employeeId,UUID companyId,long version,LocalDate effectiveFrom,LocalDate serviceStart,UUID policyId,long policyVersion,BigDecimal dailyHours,BigDecimal weeklyHours,BigDecimal weeklyDays,BigDecimal comparableDailyHours,BigDecimal comparableWeeklyHours,String category,String evidence,boolean partTime){}
 public record ContractWrite(Long expectedVersion,LocalDate effectiveFrom,LocalDate serviceStart,UUID policyId,Long policyVersion,BigDecimal dailyHours,BigDecimal weeklyHours,BigDecimal weeklyDays,BigDecimal comparableDailyHours,BigDecimal comparableWeeklyHours,String category,String evidence){}
 public record ContractPage(UUID employeeId,String employeeName,boolean canManage,List<Contract> items,List<Policy> policies,UUID companyId,LocalDate suggestedServiceStart,long version){}
}
