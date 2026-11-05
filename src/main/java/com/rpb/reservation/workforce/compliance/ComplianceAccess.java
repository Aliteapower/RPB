package com.rpb.reservation.workforce.compliance;
import com.rpb.reservation.workforce.application.*;import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.walkin.api.CurrentActor;import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.UUID;import org.springframework.stereotype.Component;
@Component public class ComplianceAccess {
 private final WorkforceAccess gate;private final WorkforceEmployeeRepository employees;private final com.rpb.reservation.workforce.persistence.WorkforceDirectory directory;
 public ComplianceAccess(WorkforceAccess gate,WorkforceEmployeeRepository employees,com.rpb.reservation.workforce.persistence.WorkforceDirectory directory){this.gate=gate;this.employees=employees;this.directory=directory;}
 public WorkforceDtos.Employee employee(CurrentActor a,UUID store,UUID id,boolean write){gate.enter(a,store);var e=employees.find(a.tenantId(),id).orElseThrow(WorkforceException::missing);if(!e.storeIds().contains(store))throw WorkforceException.missing();boolean manager=gate.managedStores(a).containsAll(e.storeIds());if(write){gate.manage(a,store);if(!manager)throw WorkforceException.forbidden();}else if(!manager&&!a.actorId().equals(e.accountId()))throw WorkforceException.forbidden();return e;}
 public void headquarters(CurrentActor a,UUID store){gate.enter(a,store);gate.headOffice(a);gate.manage(a,store);for(var option:directory.stores(a.tenantId())){if(!a.canAccessStore(option.id()))throw WorkforceException.forbidden();gate.enter(a,option.id());}}
 public void notSelf(CurrentActor a,WorkforceDtos.Employee e){if(a.actorId().equals(e.accountId()))throw new WorkforceException("WORKFORCE_SELF_REVIEW",403);}
}
