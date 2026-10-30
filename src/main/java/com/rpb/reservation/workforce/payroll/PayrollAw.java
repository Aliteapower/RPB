package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.statutory.annual.AnnualWageCalculator.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.statutory.annual.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/** Bridges the normal payroll engine to the shared annual allocator. No independent wage formula. */
final class PayrollAw {
 private PayrollAw(){}
 record Decision(StatutoryDtos.Result cpf,PayrollDtos.AwCalculation trace){}
 static Decision calculate(PayrollSources.CapturedLine line,YearMonth month,BigDecimal gross,StatutoryPolicies.Policy policy){
  WageEvents.month(line.input().items(),month);var events=AnnualWageRepository.events(line.input(),"approved-month:"+line.input().version());var rawAW=AnnualWageRepository.aw(events);var rawOW=gross.subtract(rawAW);
  if(rawAW.signum()==0)return new Decision(new StatutoryCalculator().calculate(month,line.profile().cpf(),gross,gross,policy.config()),null);
  BigDecimal eligible=null;AnnualWageDtos.Assessment assessment=null;
  boolean exempt="foreign".equals(line.profile().cpf().citizenship());
  if(!exempt){if(policy.config().aw()==null)throw new WorkforceException("STATUTORY_CAPABILITY_UNSUPPORTED",409);if(line.annual()==null)throw new WorkforceException("ANNUAL_WAGES_INCOMPLETE",409);
   var context=AnnualWageRepository.current(line.annual(),month,rawOW,line.profile().cpf(),policy,events);assessment=new AnnualWageCalculator().assess(context);
   if(assessment.awCeiling()==null)throw new WorkforceException(assessment.issues().isEmpty()?"ANNUAL_WAGES_INCOMPLETE":assessment.issues().getFirst(),409);
   eligible=assessment.allocations().stream().filter(a->a.month().equals(month.toString())).map(a->amount(a.eligibleAW())).reduce(BigDecimal.ZERO,BigDecimal::add);
  }
  var result=new StatutoryCalculator().calculateMonthly(month,line.profile().cpf(),rawOW,rawAW,eligible,policy.config());
  return new Decision(result,new PayrollDtos.AwCalculation(exempt?"not_applicable":"ready",money(rawOW),money(rawAW),money(gross),exempt?"0.00":money(rawOW.min(StatutoryPolicyConfig.number(policy.config().owCeiling()))),exempt?"0.00":money(eligible),assessment==null?null:assessment.annualOW(),assessment==null?null:assessment.awCeiling(),assessment==null?null:assessment.remainingAW(),line.annual()==null?null:line.annual().fingerprint(),List.of()));
 }
 static PayrollDtos.AwCalculation missing(PayrollSources.CapturedLine line,BigDecimal gross,String issue){var aw=AnnualWageRepository.aw(AnnualWageRepository.events(line.input(),"input"));return new PayrollDtos.AwCalculation("incomplete",money(gross.subtract(aw)),money(aw),money(gross),null,null,null,null,null,line.annual()==null?null:line.annual().fingerprint(),List.of(issue));}
}
