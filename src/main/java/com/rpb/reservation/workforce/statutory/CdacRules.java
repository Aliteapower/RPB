package com.rpb.reservation.workforce.statutory;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.*;import java.time.*;import java.net.URI;import java.util.*;
/** Monthly wage brackets, independent of CPF age bands and the CPF wage ceiling. */
public final class CdacRules {
 private CdacRules(){}
 public record Band(String upperWage,String amount){}
 public record Policy(LocalDate effectiveFrom,LocalDate effectiveTo,String source,List<Band> bands){}
 public record Selected(String lowerWage,String upperWage,BigDecimal amount){}
 public static Policy official(int year){if(year!=2026&&year!=2027)throw WorkforceException.invalid();return new Policy(LocalDate.of(year,1,1),LocalDate.of(year,12,31),"https://www.cpf.gov.sg/employer/employer-obligations/contributions-to-self-help-groups",List.of(new Band("2000","0.50"),new Band("3500","1.00"),new Band("5000","1.50"),new Band("7500","2.00"),new Band(null,"3.00")));}
 public static void validate(Policy p){
  if(p==null||p.effectiveFrom()==null||p.effectiveTo()==null||p.effectiveFrom().getDayOfMonth()!=1||!p.effectiveTo().equals(YearMonth.from(p.effectiveTo()).atEndOfMonth())||p.effectiveTo().isBefore(p.effectiveFrom())||p.bands()==null||p.bands().isEmpty()||p.bands().size()>20)throw WorkforceException.invalid();
  try{var u=URI.create(p.source());if(p.source().length()>1000||!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw WorkforceException.invalid();}catch(IllegalArgumentException|NullPointerException e){throw WorkforceException.invalid();}
  BigDecimal previous=BigDecimal.ZERO;for(int i=0;i<p.bands().size();i++){var b=p.bands().get(i);if(b==null)throw WorkforceException.invalid();var amount=StatutoryPolicyConfig.number(b.amount());if(amount.scale()>2)throw WorkforceException.invalid();if(i==p.bands().size()-1){if(b.upperWage()!=null)throw WorkforceException.invalid();}else{if(b.upperWage()==null)throw WorkforceException.invalid();var upper=StatutoryPolicyConfig.number(b.upperWage());if(upper.scale()>2||upper.compareTo(previous)<=0)throw WorkforceException.invalid();previous=upper;}}
 }
 public static Selected amount(Policy p,YearMonth month,BigDecimal wages){
  validate(p);if(month==null||wages==null||wages.signum()<0)throw WorkforceException.invalid();if(month.atDay(1).isBefore(p.effectiveFrom())||month.atEndOfMonth().isAfter(p.effectiveTo()))throw new WorkforceException("CDAC_POLICY_MISSING",409);
  String lower="0";for(var band:p.bands()){if(band.upperWage()==null||wages.compareTo(new BigDecimal(band.upperWage()))<=0)return new Selected(lower,band.upperWage(),wages.signum()==0?BigDecimal.ZERO:new BigDecimal(band.amount()));lower=band.upperWage();}throw WorkforceException.invalid();
 }
}
