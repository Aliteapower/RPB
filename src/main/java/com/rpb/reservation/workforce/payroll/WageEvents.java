package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;
import java.util.*;

/** Contract evidence for one economic income; payroll labels never determine CPF classification. */
public final class WageEvents {
 private WageEvents(){}
 public record Evidence(UUID eventId,String businessType,String classification,LocalDate serviceFrom,LocalDate serviceTo,LocalDate decisionDate,LocalDate contractualPayableDate,String contributionMonth,String settlementMonth,String evidenceReference,String classificationReason){}
 public static Evidence validate(Evidence e,String kind){
  if(e==null){if("wage_event".equals(kind))throw missing();return null;}
  if(!"wage_event".equals(kind)||e.eventId()==null||!Set.of("bonus","aws","retroactive_increment","omitted_wages").contains(Objects.toString(e.businessType(),""))||!Set.of("OW","AW").contains(Objects.toString(e.classification(),""))||e.serviceFrom()==null||e.serviceTo()==null||e.serviceTo().isBefore(e.serviceFrom())||e.contractualPayableDate()==null||e.evidenceReference()==null||e.evidenceReference().isBlank()||e.evidenceReference().length()>1000||e.classificationReason()==null||e.classificationReason().isBlank()||e.classificationReason().length()>1000)throw missing();
  YearMonth contribution=PayrollProfileValidation.month(e.contributionMonth()),settlement=PayrollProfileValidation.month(e.settlementMonth());
  boolean ordinary=YearMonth.from(e.serviceFrom()).equals(YearMonth.from(e.serviceTo()))&&!e.contractualPayableDate().isAfter(YearMonth.from(e.serviceTo()).plusMonths(1).atDay(14));
  if(e.businessType().equals("retroactive_increment")){if(e.decisionDate()==null||e.decisionDate().isBefore(e.serviceTo())||e.contractualPayableDate().isBefore(e.decisionDate())||!e.classification().equals("AW")||!YearMonth.from(e.decisionDate()).equals(contribution)||!YearMonth.from(e.contractualPayableDate()).equals(contribution))throw missing();}
  else if(ordinary!=e.classification().equals("OW"))throw missing();
  if(e.classification().equals("OW")&&!contribution.equals(YearMonth.from(e.serviceTo()))||e.classification().equals("AW")&&!contribution.equals(YearMonth.from(e.contractualPayableDate()))||settlement.isBefore(contribution))throw missing();
  return e;
 }
 public static void month(List<PayrollDtos.Item> items,YearMonth month){var seen=new HashSet<UUID>();for(var item:items){var e=validate(item.eventEvidence(),item.kind());if(e!=null&&(e.businessType().equals("omitted_wages")||!month.toString().equals(e.settlementMonth())||!month.toString().equals(e.contributionMonth())||!seen.add(e.eventId())))throw new WorkforceException("WAGE_EVENT_PERIOD_UNSUPPORTED",409);}}
 private static WorkforceException missing(){return new WorkforceException("WAGE_CLASSIFICATION_EVIDENCE_MISSING",400);}
}
