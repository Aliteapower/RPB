package com.rpb.reservation.workforce.payroll.supplemental;

import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;

public final class SupplementalValidation {
 private SupplementalValidation(){}
 public static List<SupplementalDtos.Proposal> proposals(List<SupplementalDtos.Proposal> values){
  if(values.isEmpty())return null;if(values.size()>24)throw WorkforceException.invalid();var ids=new HashSet<UUID>();var events=new HashSet<UUID>();
  for(var p:values){if(p==null||p.id()==null||!ids.add(p.id())||p.source()==null||p.source().snapshotHash()==null||p.source().snapshotHash().isBlank()||!Set.of("normal","opening","employment-zero").contains(Objects.toString(p.source().kind(),""))||p.source().kind().equals("normal")&&p.source().batchId()==null||p.source().kind().equals("opening")&&(p.source().openingVersion()==null||p.source().openingVersion()<1)||p.evidenceReference()==null||p.evidenceReference().isBlank()||p.evidenceReference().length()>1000||p.items()==null||p.items().isEmpty()||p.items().size()>20)throw WorkforceException.invalid();
   var origin=PayrollProfileValidation.month(p.originMonth());var settlement=PayrollProfileValidation.month(p.settlementMonth());if(settlement.isBefore(origin))throw WorkforceException.invalid();
   for(var i:p.items()){if(i==null||!"wage_event".equals(i.kind())||i.label()==null||i.label().isBlank()||i.label().length()>100||i.proration()!=null&&!i.proration().equals("full")||i.source()!=null||i.amount()==null||!i.amount().matches("[0-9]{1,8}(\\.[0-9]{1,2})?")||SupplementalMoney.number(i.amount()).signum()<=0)throw WorkforceException.invalid();var e=WageEvents.validate(i.eventEvidence(),"wage_event");if(e==null||!events.add(e.eventId())||!e.contributionMonth().equals(p.originMonth())||!e.settlementMonth().equals(p.settlementMonth()))throw new WorkforceException("WAGE_EVENT_PERIOD_UNSUPPORTED",409);}
  }return List.copyOf(values);
 }
}
