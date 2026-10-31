package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;import java.time.*;import java.util.*;
/** Applies an explicitly reviewed eligibility choice; never infers race from a name or identity number. */
public final class CdacPayroll {
 private CdacPayroll(){}
 public record Decision(BigDecimal deduction,CdacCalculation calculation){}
 public static CdacProfile profile(CdacProfile c,StatutoryDtos.CpfProfile cpf,Rule rule){
  if(c==null)return null;if(c.status()==null||!Set.of("applicable","not_applicable","exempt","manual").contains(c.status()))throw WorkforceException.invalid();
  String ref=PayrollValues.text(c.reference(),500,Set.of("exempt","manual").contains(c.status()));
  if(c.status().equals("applicable")&&"foreign".equals(cpf.citizenship())||!c.status().equals("manual")&&rule.items().stream().anyMatch(i->i.kind().equals("cdac")))throw WorkforceException.invalid();
  return new CdacProfile(c.status(),ref);
 }
 public static CdacReview review(CdacReview r){if(r==null)return null;return new CdacReview(PayrollValues.money(PayrollValues.decimal(r.amount())),PayrollValues.text(r.note(),500,true));}
 public static Decision evaluate(YearMonth month,PayProfile profile,LineInput input,StatutoryPolicies.Policy policy,BigDecimal gross){
  var setting=profile.cdac();var manual=new ArrayList<Item>(profile.rule().items());manual.addAll(input.items());var cdac=manual.stream().filter(i->i.kind().equals("cdac")).toList();
  if(setting==null||setting.status().equals("manual")){
   if(input.cdacReview()!=null)throw new WorkforceException("CDAC_REVIEW_INVALID",409);
   var total=cdac.stream().map(i->new BigDecimal(i.amount())).reduce(BigDecimal.ZERO,BigDecimal::add);
   return new Decision(BigDecimal.ZERO,setting==null?null:new CdacCalculation("manual",PayrollValues.money(gross),null,PayrollValues.money(total),null,null,""));
  }
  if(!cdac.isEmpty())throw new WorkforceException("CDAC_DUPLICATE",409);
  if(Set.of("exempt","not_applicable").contains(setting.status())){
   if(input.cdacReview()!=null)throw new WorkforceException("CDAC_REVIEW_INVALID",409);
   if(setting.status().equals("exempt")&&(setting.reference()==null||setting.reference().isBlank()))throw new WorkforceException("CDAC_PROFILE_INVALID",409);
   return new Decision(BigDecimal.ZERO,new CdacCalculation(setting.status(),PayrollValues.money(gross),"0.00","0.00",null,null,""));
  }
  if(!setting.status().equals("applicable")||"foreign".equals(profile.cpf().citizenship()))throw new WorkforceException("CDAC_PROFILE_INVALID",409);
  if(policy==null||!policy.enabled()||policy.config().cdac()==null||month.atDay(1).isBefore(policy.effectiveFrom())||month.atEndOfMonth().isAfter(policy.effectiveTo()))throw new WorkforceException("CDAC_POLICY_MISSING",409);
  var selected=CdacRules.amount(policy.config().cdac(),month,gross);var override=input.cdacReview();
  if(override!=null)try{review(override);}catch(WorkforceException e){throw new WorkforceException("CDAC_REVIEW_INVALID",409);}
  var amount=override==null?selected.amount():new BigDecimal(override.amount());
  return new Decision(amount,new CdacCalculation(override==null?"automatic":"override",PayrollValues.money(gross),PayrollValues.money(selected.amount()),PayrollValues.money(amount),selected.lowerWage(),selected.upperWage(),policy.id()+"/v"+policy.version()));
 }
}
