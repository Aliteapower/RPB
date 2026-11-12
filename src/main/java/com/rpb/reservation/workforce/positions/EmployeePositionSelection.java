package com.rpb.reservation.workforce.positions;
import com.rpb.reservation.workforce.application.WorkforceDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.springframework.stereotype.Service;
/** Resolve modern IDs and legacy text without allowing arbitrary new job values. */
@Service
public class EmployeePositionSelection {
 private final PositionRepository repository;
 public EmployeePositionSelection(PositionRepository repository){this.repository=repository;}
 public void lock(UUID tenant){repository.lock(tenant);}
 public EmployeeWrite resolve(UUID tenant,EmployeeWrite c,Employee old){
  UUID id=c.positionId();String title=c.jobTitle();
  if(id==null&&!title.isEmpty()){
   var match=repository.list(tenant).stream().filter(p->p.name().equalsIgnoreCase(c.jobTitle())).findFirst();
   if(match.isPresent())id=match.get().id();
   else if(old!=null&&old.positionId()==null&&old.jobTitle().equals(title))return c;
   else throw new WorkforceException("WORKFORCE_POSITION_UNAVAILABLE",400);
  }
  if(id!=null){var p=repository.find(tenant,id).orElseThrow(WorkforceException::missing);
   if(!p.enabled()&&(old==null||!id.equals(old.positionId())))throw new WorkforceException("WORKFORCE_POSITION_UNAVAILABLE",400);title=p.name();
  }
  return new EmployeeWrite(c.expectedVersion(),c.employeeNo(),c.name(),c.phone(),c.email(),title,c.hireDate(),c.leaveDate(),c.status(),c.companyId(),c.companyEffectiveDate(),c.mainStoreId(),c.storeIds(),c.accountId(),c.username(),c.password(),id,c.birthDate(),c.englishName(),c.enabled());
 }
}
