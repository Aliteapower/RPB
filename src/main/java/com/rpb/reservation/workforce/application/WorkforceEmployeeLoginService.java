package com.rpb.reservation.workforce.application;

import com.rpb.reservation.auth.application.WorkforceLoginCredentials;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.persistence.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class WorkforceEmployeeLoginService {
 public record Account(UUID accountId,String username,String status,long version,boolean workforceAccess,boolean canReset){}
 public record Write(Long expectedVersion,String password){}
 private final WorkforceAccess access;private final WorkforceEmployeeService employees;private final WorkforceEmployeeRepository repository;private final WorkforceDirectory directory;private final WorkforceLoginCredentials credentials;private final WorkforceAudit audit;
 public WorkforceEmployeeLoginService(WorkforceAccess access,WorkforceEmployeeService employees,WorkforceEmployeeRepository repository,WorkforceDirectory directory,WorkforceLoginCredentials credentials,WorkforceAudit audit){this.access=access;this.employees=employees;this.repository=repository;this.directory=directory;this.credentials=credentials;this.audit=audit;}
 public Account get(CurrentActor actor,UUID store,UUID id){var employee=employees.get(actor,store,id);if(!employee.editable())throw WorkforceException.forbidden();if(employee.accountId()==null)return null;return view(actor,credentials.find(actor.tenantId(),employee.accountId(),false).orElseThrow(WorkforceException::missing));}
 @Transactional public Account reset(CurrentActor actor,UUID store,UUID id,Write c){
  access.sourceWrite(actor);access.enter(actor,store);access.manage(actor,store);
  if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.password()==null||!c.password().matches("[A-Za-z0-9]{6}"))throw WorkforceException.invalid();
  repository.lock(id);var employee=employees.get(actor,store,id);if(!employee.editable())throw WorkforceException.forbidden();if(employee.accountId()==null)throw WorkforceException.missing();
  var locked=credentials.find(actor.tenantId(),employee.accountId(),true).orElseThrow(WorkforceException::missing);var before=view(actor,locked);if(!before.canReset())throw WorkforceException.forbidden();
  try{var changed=credentials.reset(actor.tenantId(),locked,c.expectedVersion(),c.password());var after=view(actor,changed.info());if(changed.changed())audit.changed(actor,store,"employee.password.reset",id,before,after);return after;}catch(WorkforceLoginCredentials.Conflict e){throw WorkforceException.conflict();}
 }
 private Account view(CurrentActor actor,WorkforceLoginCredentials.Info info){
  var accountStores=directory.accountStores(actor.tenantId(),info.accountId());
  boolean subordinate=access.isHeadOffice(actor)||(directory.ordinaryAccount(actor.tenantId(),info.accountId())&&directory.managerStoreIds(actor.tenantId(),info.accountId()).isEmpty());
  boolean can=info.resetEligible()&&subordinate&&!info.accountId().equals(actor.actorId())&&!accountStores.isEmpty()&&access.managedStores(actor).containsAll(accountStores);
  return new Account(info.accountId(),info.username(),info.status(),info.version(),info.workforceAccess(),can);
 }
}
