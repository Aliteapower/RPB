package com.rpb.reservation.workforce.scheduling.bulk;
import static com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleDtos.*;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BulkScheduleService {
 private final WorkforceAccess access;private final BulkScheduleRepository repository;private final SchedulingRepository scheduling;private final WorkforceEmployeeRepository employees;private final BulkSchedulePlanService plans;private final ScheduleService schedules;private final WorkforceAudit audit;private final WorkforceJson json;
 public BulkScheduleService(WorkforceAccess access,BulkScheduleRepository repository,SchedulingRepository scheduling,WorkforceEmployeeRepository employees,BulkSchedulePlanService plans,ScheduleService schedules,WorkforceAudit audit,WorkforceJson json){this.access=access;this.repository=repository;this.scheduling=scheduling;this.employees=employees;this.plans=plans;this.schedules=schedules;this.audit=audit;this.json=json;}
 @Transactional public Result commit(CurrentActor actor,UUID store,UUID command,Commit input){
  access.sourceWrite(actor);access.enter(actor,store);access.manage(actor,store);if(command==null||input==null||input.fingerprint()==null||!input.fingerprint().matches("[a-f0-9]{64}"))throw WorkforceException.invalid();
  var request=BulkSchedulePlanService.normalize(input.request());var hash=BulkSchedulePlanService.hash(json.write(new Commit(request,input.fingerprint())));scheduling.lockPlanning(actor.tenantId(),store,false);scheduling.lock("workforce.bulk:"+actor.tenantId()+":"+command);
  var replay=repository.replay(actor.tenantId(),store,actor.actorId(),command,hash);if(replay.isPresent())return replay.get();
  repository.lockStore(actor.tenantId(),store);scheduling.lock("workforce.shift:"+request.shiftId());request.employeeIds().forEach(employees::lock);var preview=plans.preview(actor,store,request);
  if(!input.fingerprint().equals(preview.fingerprint()))throw new WorkforceException("WORKFORCE_BULK_CHANGED",409);
  if(preview.ready()==0)throw WorkforceException.invalid();
  var ids=new ArrayList<UUID>();for(var item:preview.items())if(item.status().equals("ready")){var id=UUID.randomUUID();schedules.save(actor,store,id,new SchedulingDtos.ScheduleWrite(0L,item.employeeId(),request.shiftId(),item.date(),"scheduled"));ids.add(id);}
  var result=new Result(command,ids.size(),preview.existing(),preview.conflicts(),List.copyOf(ids));repository.receipt(actor.tenantId(),store,actor.actorId(),command,hash,result);audit.changed(actor,store,"schedule.bulk",command,request,result);return result;
 }
}
