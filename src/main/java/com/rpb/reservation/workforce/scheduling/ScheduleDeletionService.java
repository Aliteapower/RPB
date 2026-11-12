package com.rpb.reservation.workforce.scheduling;
import static com.rpb.reservation.workforce.scheduling.ScheduleDeletionDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ScheduleDeletionService {
 private final WorkforceAccess access;private final SchedulingRepository schedules;private final WorkforceEmployeeRepository employees;private final WorkforceAudit audit;
 public ScheduleDeletionService(WorkforceAccess access,SchedulingRepository schedules,WorkforceEmployeeRepository employees,WorkforceAudit audit){this.access=access;this.schedules=schedules;this.employees=employees;this.audit=audit;}
 @Transactional public Result delete(CurrentActor actor,UUID store,UUID id,Request input){
  access.sourceWrite(actor);access.enter(actor,store);access.manage(actor,store);if(input==null||input.expectedVersion()==null||input.expectedVersion()<1)throw WorkforceException.invalid();
  schedules.lockPlanning(actor.tenantId(),store,false);var initial=schedules.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);if(!store.equals(schedules.store(actor.tenantId(),id)))throw WorkforceException.forbidden();employees.lock(initial.employeeId());schedules.lock("workforce.schedule:"+id);
  var row=schedules.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
  if(schedules.deleted(actor.tenantId(),id)){if(row.version()==input.expectedVersion()+1)return new Result(id,row.version(),true);throw WorkforceException.conflict();}
  if(row.version()!=input.expectedVersion())throw WorkforceException.conflict();if(schedules.used(actor.tenantId(),id))throw new WorkforceException("WORKFORCE_ACTIVE_ATTENDANCE",409);
  schedules.softDelete(actor.tenantId(),store,id,row.version());var result=new Result(id,row.version()+1,true);audit.changed(actor,store,"schedule.delete",id,row,result);return result;
 }
}
