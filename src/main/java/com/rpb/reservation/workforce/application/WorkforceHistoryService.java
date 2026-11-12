package com.rpb.reservation.workforce.application;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.payroll.PayrollAccess;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minimal historical context, without operational home/settings or employee directories. */
@Service @Transactional(readOnly=true)
public class WorkforceHistoryService {
 private final WorkforceAccess access;private final PayrollAccess payroll;private final WorkforceEmployeeRepository employees;
 public WorkforceHistoryService(WorkforceAccess access,PayrollAccess payroll,WorkforceEmployeeRepository employees){this.access=access;this.payroll=payroll;this.employees=employees;}
 public record Context(boolean employeeManage,boolean self,List<WorkforceDtos.Option> companies){}
 public Context context(CurrentActor actor,UUID store){access.history(actor,store);return new Context(access.managedStores(actor).contains(store),employees.byAccount(actor.tenantId(),actor.actorId()).isPresent(),payroll.historyCompanies(actor,store));}
}
