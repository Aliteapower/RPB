package com.rpb.reservation.workforce.scheduling.adjustment;
import static com.rpb.reservation.workforce.scheduling.adjustment.BulkAdjustmentDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.bulk.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;import java.util.*;import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class BulkAdjustmentPlanService {
 private final WorkforceAccess access;private final SchedulingRepository schedules;private final BulkScheduleRepository bulk;private final WorkforceJson json;private final Clock clock;
 public BulkAdjustmentPlanService(WorkforceAccess access,SchedulingRepository schedules,BulkScheduleRepository bulk,WorkforceJson json,Clock clock){this.access=access;this.schedules=schedules;this.bulk=bulk;this.json=json;this.clock=clock;}
 public static Request normalize(Request r){if(r==null||r.date()==null||r.sourceShiftId()==null||r.targetShiftId()==null||r.targets()==null||r.targets().isEmpty()||r.targets().size()>500||r.targets().stream().anyMatch(t->t==null||t.scheduleId()==null||t.expectedVersion()==null||t.expectedVersion()<1)||r.targets().stream().map(Target::scheduleId).distinct().count()!=r.targets().size())throw WorkforceException.invalid();return new Request(r.date(),r.sourceShiftId(),r.targetShiftId(),r.targets().stream().sorted(Comparator.comparing(Target::scheduleId)).toList());}
 public List<SchedulingRepository.Inspection> sources(CurrentActor actor,UUID store,Request request){access.enter(actor,store);access.manage(actor,store);var r=normalize(request);var source=schedules.inspect(actor.tenantId(),r.targets().stream().map(Target::scheduleId).toList());if(source.size()!=r.targets().size())throw WorkforceException.missing();if(source.stream().anyMatch(s->!s.store().equals(store)))throw WorkforceException.forbidden();if(source.stream().map(s->s.schedule().employeeId()).distinct().count()!=source.size())throw WorkforceException.invalid();return source;}
 public Preview preview(CurrentActor actor,UUID store,Request input){
  var request=normalize(input);var source=sources(actor,store,request);var target=schedules.shift(actor.tenantId(),store,request.targetShiftId()).orElseThrow(WorkforceException::missing);String timezone=schedules.timezone(actor.tenantId(),store);var zone=ZoneId.of(timezone);var today=LocalDate.now(clock.withZone(zone));Instant start=null,end=null;
  try{var interval=ShiftTiming.resolve(request.date(),target,zone);start=interval.start();end=interval.end();if(!end.isAfter(start)||Duration.between(start,end).getSeconds()>86400){start=null;end=null;}}catch(DateTimeException|WorkforceException invalid){start=null;end=null;}
  var employees=source.stream().map(s->s.schedule().employeeId()).sorted().toList();var states=bulk.states(actor.tenantId(),employees);var assignments=bulk.assignments(actor.tenantId(),store,employees);var occupied=bulk.occupied(actor.tenantId(),employees,request.date(),request.date(),request.date().atStartOfDay(zone).toInstant(),request.date().plusDays(3).atStartOfDay(zone).toInstant());var occupiedMap=occupied.stream().collect(Collectors.groupingBy(BulkScheduleRepository.Occupied::employeeId));var expected=request.targets().stream().collect(Collectors.toMap(Target::scheduleId,Target::expectedVersion));var items=new ArrayList<Item>();int ready=0,unchanged=0,conflicts=0;
  for(var inspection:source){var row=inspection.schedule();String status="ready",reason="";
   if(inspection.deleted()||!row.status().equals("scheduled")||row.version()!=expected.get(row.id())||!row.workDate().equals(request.date())||!row.shiftId().equals(request.sourceShiftId()))reason="SOURCE_CHANGED";
   else if(request.sourceShiftId().equals(request.targetShiftId())){status="unchanged";reason="UNCHANGED";}
   else if(inspection.used())reason="IN_USE";
   else if(inspection.segments()!=1)reason="MULTIPLE_SEGMENTS";
   else if(request.date().isBefore(today)||request.date().isAfter(today.plusDays(90)))reason="OUTSIDE_WINDOW";
   else if(!target.enabled())reason="SHIFT_UNAVAILABLE";
   else if(start==null||end==null)reason="INVALID_TIME";
   else if(!schedules.assigned(actor.tenantId(),row.employeeId(),store,request.date()))reason="NOT_ASSIGNED";
   else {final var from=start;final var to=end;if(occupiedMap.getOrDefault(row.employeeId(),List.of()).stream().anyMatch(s->!s.id().equals(row.id())&&s.start().isBefore(to)&&s.end().isAfter(from)))reason="TIME_OVERLAP";}
   if(status.equals("unchanged"))unchanged++;else if(!reason.isEmpty()){status="conflict";conflicts++;}else ready++;
   items.add(new Item(row.id(),row.employeeId(),row.employeeNo(),row.employeeName(),row.workDate(),row.version(),row.shift().name(),row.startAt(),row.endAt(),row.timezone(),start,end,status,reason));
  }
  String fingerprint=BulkSchedulePlanService.hash(json.write(List.of("schedule-adjustment",request,timezone,target,source,states,assignments,occupied,items)));
  return new Preview(fingerprint,request.date(),request.sourceShiftId(),request.targetShiftId(),target.name(),timezone,items.size(),ready,unchanged,conflicts,List.copyOf(items));
 }
}

