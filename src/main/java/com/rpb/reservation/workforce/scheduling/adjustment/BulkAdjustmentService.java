package com.rpb.reservation.workforce.scheduling.adjustment;
import static com.rpb.reservation.workforce.scheduling.adjustment.BulkAdjustmentDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.bulk.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service
public class BulkAdjustmentService {
 private final WorkforceAccess access;private final SchedulingRepository scheduling;private final BulkScheduleRepository bulk;private final WorkforceEmployeeRepository employees;private final BulkAdjustmentPlanService plans;private final ScheduleService schedules;private final WorkforceAudit audit;private final WorkforceJson json;
 public BulkAdjustmentService(WorkforceAccess access,SchedulingRepository scheduling,BulkScheduleRepository bulk,WorkforceEmployeeRepository employees,BulkAdjustmentPlanService plans,ScheduleService schedules,WorkforceAudit audit,WorkforceJson json){this.access=access;this.scheduling=scheduling;this.bulk=bulk;this.employees=employees;this.plans=plans;this.schedules=schedules;this.audit=audit;this.json=json;}
 @Transactional public Result commit(CurrentActor actor,UUID store,UUID command,Commit input){
  access.sourceWrite(actor);access.enter(actor,store);access.manage(actor,store);if(command==null||input==null||input.fingerprint()==null||!input.fingerprint().matches("[a-f0-9]{64}"))throw WorkforceException.invalid();var request=BulkAdjustmentPlanService.normalize(input.request());String hash=BulkSchedulePlanService.hash("schedule-adjustment:"+json.write(new Commit(request,input.fingerprint())));
  scheduling.lockPlanning(actor.tenantId(),store,false);scheduling.lock("workforce.bulk:"+actor.tenantId()+":"+command);var replay=bulk.replayAs(actor.tenantId(),store,actor.actorId(),command,hash,Result.class);if(replay.isPresent())return replay.get();bulk.lockStore(actor.tenantId(),store);scheduling.lock("workforce.shift:"+request.targetShiftId());var source=plans.sources(actor,store,request);source.stream().map(s->s.schedule().employeeId()).distinct().sorted().forEach(employees::lock);request.targets().forEach(t->scheduling.lock("workforce.schedule:"+t.scheduleId()));
  var preview=plans.preview(actor,store,request);if(!preview.fingerprint().equals(input.fingerprint()))throw new WorkforceException("WORKFORCE_ADJUST_CHANGED",409);if(preview.ready()==0)throw WorkforceException.invalid();var updated=new ArrayList<UUID>();for(var row:preview.items())if(row.status().equals("ready")){schedules.save(actor,store,row.scheduleId(),new SchedulingDtos.ScheduleWrite(row.version(),row.employeeId(),request.targetShiftId(),request.date(),"scheduled"));updated.add(row.scheduleId());}
  var result=new Result(command,updated.size(),preview.unchanged(),preview.conflicts(),List.copyOf(updated));bulk.receiptValue(actor.tenantId(),store,actor.actorId(),command,hash,result);audit.changed(actor,store,"schedule.adjust.bulk",command,request,result);return result;
 }
}
