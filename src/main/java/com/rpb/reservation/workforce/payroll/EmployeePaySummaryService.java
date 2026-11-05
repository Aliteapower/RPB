package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;import java.time.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class EmployeePaySummaryService {
 private final PayrollAccess access;private final PayrollConfigurationRepository repository;
 public EmployeePaySummaryService(PayrollAccess access,PayrollConfigurationRepository repository){this.access=access;this.repository=repository;}
 public EmployeePaySummary summary(CurrentActor actor,UUID store,UUID employee,UUID company,LocalDate on){
  access.enter(actor,store,company);access.employee(actor,company,employee);if(on==null)throw WorkforceException.invalid();
  var profiles=repository.employeeProfiles(actor.tenantId(),company,employee);var result=new ArrayList<ProfilePeriod>();
  for(int i=0;i<profiles.size();i++){var p=profiles.get(i);LocalDate to=i+1<profiles.size()?profiles.get(i+1).effectiveFrom().minusDays(1):null;String state=p.effectiveFrom().isAfter(on)?"future":to!=null&&to.isBefore(on)?"history":"current";result.add(new ProfilePeriod(p,to,state,p.origin()!=null));}
  return new EmployeePaySummary(employee,company,on,List.copyOf(result));
 }
}
