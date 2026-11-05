package com.rpb.reservation.workforce.scheduling.copy;
import static com.rpb.reservation.workforce.scheduling.copy.WeekCopyDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.WorkforceEmployeeRepository;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.bulk.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class WeekCopyService {
 private final WorkforceAccess access;private final SchedulingRepository scheduling;private final BulkScheduleRepository bulk;private final WorkforceEmployeeRepository employees;private final WeekCopyPlanService plans;private final ScheduleService schedules;private final WorkforceAudit audit;private final WorkforceJson json;
 public WeekCopyService(WorkforceAccess access,SchedulingRepository scheduling,BulkScheduleRepository bulk,WorkforceEmployeeRepository employees,WeekCopyPlanService plans,ScheduleService schedules,WorkforceAudit audit,WorkforceJson json){this.access=access;this.scheduling=scheduling;this.bulk=bulk;this.employees=employees;this.plans=plans;this.schedules=schedules;this.audit=audit;this.json=json;}
 @Transactional public BulkScheduleDtos.Result commit(CurrentActor actor,UUID store,UUID command,Commit input){
  access.sourceWrite(actor);access.enter(actor,store);access.manage(actor,store);if(command==null||input==null||input.fingerprint()==null||!input.fingerprint().matches("[a-f0-9]{64}"))throw WorkforceException.invalid();var request=WeekCopyPlanService.normalize(input.request());String hash=BulkSchedulePlanService.hash("week-copy:"+json.write(new Commit(request,input.fingerprint())));
  scheduling.lockPlanning(actor.tenantId(),store,true);scheduling.lock("workforce.bulk:"+actor.tenantId()+":"+command);var replay=bulk.replay(actor.tenantId(),store,actor.actorId(),command,hash);if(replay.isPresent())return replay.get();
  bulk.lockStore(actor.tenantId(),store);var source=plans.source(actor,store,request);source.stream().map(SchedulingDtos.Schedule::shiftId).distinct().sorted().forEach(id->scheduling.lock("workforce.shift:"+id));source.stream().map(SchedulingDtos.Schedule::employeeId).distinct().sorted().forEach(employees::lock);
  var preview=plans.preview(actor,store,request);if(!preview.fingerprint().equals(input.fingerprint()))throw new WorkforceException("WORKFORCE_COPY_CHANGED",409);if(preview.ready()==0)throw WorkforceException.invalid();
  var ids=new ArrayList<UUID>();for(var row:preview.items())if(row.status().equals("ready")){var id=UUID.randomUUID();schedules.save(actor,store,id,new SchedulingDtos.ScheduleWrite(0L,row.employeeId(),row.shiftId(),row.date(),"scheduled"));ids.add(id);}
  var result=new BulkScheduleDtos.Result(command,ids.size(),preview.existing(),preview.conflicts(),List.copyOf(ids));bulk.receipt(actor.tenantId(),store,actor.actorId(),command,hash,result);audit.changed(actor,store,"schedule.week-copy",command,request,result);return result;
 }
}
