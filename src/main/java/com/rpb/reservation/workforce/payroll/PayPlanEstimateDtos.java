package com.rpb.reservation.workforce.payroll;
import java.util.*;import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
public final class PayPlanEstimateDtos {
 private PayPlanEstimateDtos(){}
 public record Request(UUID companyId,UUID employeeId,String month,PayrollDtos.Rule rule,CpfProfile cpf,PayrollDtos.CdacProfile cdac,Integer days,String dayHours){}
 public record Result(String month,int days,String dayHours,long workSeconds,PayBreakdown.Values breakdown,String policy,List<String> assumptions){}
}
