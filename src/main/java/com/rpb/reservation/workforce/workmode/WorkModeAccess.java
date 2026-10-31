package com.rpb.reservation.workforce.workmode;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
final class WorkModeAccess {
 private WorkModeAccess(){}
 static WorkforceDtos.Employee manage(WorkforceAccess gate,WorkforceEmployeeRepository employees,CurrentActor actor,UUID store,UUID employee){
  gate.enter(actor,store);gate.manage(actor,store);var e=employees.find(actor.tenantId(),employee).orElseThrow(WorkforceException::missing);
  if(!e.storeIds().contains(store)||!gate.managedStores(actor).containsAll(e.storeIds()))throw WorkforceException.forbidden();return e;
 }
 static WorkforceDtos.Employee self(WorkforceAccess gate,WorkforceEmployeeRepository employees,CurrentActor actor,UUID store){
  gate.enter(actor,store);var e=employees.byAccount(actor.tenantId(),actor.actorId()).orElseThrow(WorkforceException::forbidden);if(!e.storeIds().contains(store))throw WorkforceException.forbidden();return e;
 }
}
