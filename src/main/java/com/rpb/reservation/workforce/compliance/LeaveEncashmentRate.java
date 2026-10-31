package com.rpb.reservation.workforce.compliance;
import java.math.*;import com.rpb.reservation.workforce.domain.WorkforceException;
/** Annual-leave gross-rate valuation is separate from overtime basic-rate valuation. */
public final class LeaveEncashmentRate {
 private LeaveEncashmentRate(){}
 public static BigDecimal monthly(BigDecimal monthlyGross,BigDecimal weeklyDays,BigDecimal leaveDays){if(!ComplianceRules.positive(monthlyGross)||!ComplianceRules.positive(weeklyDays)||weeklyDays.compareTo(new BigDecimal("7"))>0||!ComplianceRules.positive(leaveDays))throw WorkforceException.invalid();return monthlyGross.multiply(new BigDecimal("12")).multiply(leaveDays).divide(new BigDecimal("52").multiply(weeklyDays),2,RoundingMode.HALF_UP);}
 public static BigDecimal unit(BigDecimal grossRate,BigDecimal units){if(!ComplianceRules.positive(grossRate)||!ComplianceRules.positive(units))throw WorkforceException.invalid();return grossRate.multiply(units).setScale(2,RoundingMode.HALF_UP);}
}
