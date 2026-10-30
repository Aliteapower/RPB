package com.rpb.reservation.workforce.scheduling.copy;
import static com.rpb.reservation.workforce.scheduling.copy.WeekCopyDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.bulk.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class WeekCopyPlanService {
 public static final int MAX_ROWS=5000,MAX_EMPLOYEES=1000;
 private final WorkforceAccess access;private final SchedulingRepository schedules;private final BulkScheduleRepository bulk;private final WorkforceJson json;private final Clock clock;
 public WeekCopyPlanService(WorkforceAccess access,SchedulingRepository schedules,BulkScheduleRepository bulk,WorkforceJson json,Clock clock){this.access=access;this.schedules=schedules;this.bulk=bulk;this.json=json;this.clock=clock;}
 public static Request normalize(Request request){if(request==null||request.sourceWeek()==null||request.targetWeek()==null||request.sourceWeek().getDayOfWeek()!=DayOfWeek.MONDAY||request.targetWeek().getDayOfWeek()!=DayOfWeek.MONDAY||request.sourceWeek().equals(request.targetWeek()))throw WorkforceException.invalid();return request;}
 public List<SchedulingDtos.Schedule> source(CurrentActor actor,UUID store,Request input){
  access.enter(actor,store);access.manage(actor,store);var request=normalize(input);var rows=schedules.copyCandidates(actor.tenantId(),store,request.sourceWeek());
  if(rows.size()>MAX_ROWS||rows.stream().map(SchedulingDtos.Schedule::employeeId).distinct().count()>MAX_EMPLOYEES)throw new WorkforceException("WORKFORCE_COPY_TOO_LARGE",400);return rows;
 }
 private record Interval(Instant start,Instant end){}
 public Preview preview(CurrentActor actor,UUID store,Request input){
  var request=normalize(input);var source=source(actor,store,request);String timezone=schedules.timezone(actor.tenantId(),store);var zone=ZoneId.of(timezone);var today=LocalDate.now(clock.withZone(zone));
  var employeeIds=source.stream().map(SchedulingDtos.Schedule::employeeId).distinct().sorted().toList();var shiftIds=source.stream().map(SchedulingDtos.Schedule::shiftId).collect(Collectors.toSet());
  var shifts=schedules.shifts(actor.tenantId(),store).stream().filter(shift->shiftIds.contains(shift.id())).sorted(Comparator.comparing(SchedulingDtos.Shift::id)).toList();var shiftMap=shifts.stream().collect(Collectors.toMap(SchedulingDtos.Shift::id,shift->shift));
  var states=employeeIds.isEmpty()?List.<BulkScheduleRepository.State>of():bulk.states(actor.tenantId(),employeeIds);var stateMap=states.stream().collect(Collectors.toMap(BulkScheduleRepository.State::id,state->state));
  var assignments=employeeIds.isEmpty()?List.<BulkScheduleRepository.Assignment>of():bulk.assignments(actor.tenantId(),store,employeeIds);var assignmentMap=assignments.stream().collect(Collectors.groupingBy(BulkScheduleRepository.Assignment::employeeId));
  var occupied=employeeIds.isEmpty()?List.<BulkScheduleRepository.Occupied>of():bulk.occupied(actor.tenantId(),employeeIds,request.targetWeek(),request.targetWeek().plusDays(6),request.targetWeek().atStartOfDay(zone).toInstant(),request.targetWeek().plusDays(9).atStartOfDay(zone).toInstant());var occupiedMap=occupied.stream().collect(Collectors.groupingBy(BulkScheduleRepository.Occupied::employeeId));
  var proposed=new HashMap<UUID,List<Interval>>();var items=new ArrayList<Item>();int ready=0,existing=0,conflicts=0;long delta=ChronoUnit.DAYS.between(request.sourceWeek(),request.targetWeek());
  for(var row:source){
   var date=row.workDate().plusDays(delta);var shift=shiftMap.get(row.shiftId());var employee=stateMap.get(row.employeeId());var employeeRows=occupiedMap.getOrDefault(row.employeeId(),List.of());String status="ready",reason="";Instant start=null,end=null;
   if(shift!=null){try{var interval=ShiftTiming.resolve(date,shift,zone);start=interval.start();end=interval.end();if(!end.isAfter(start)||Duration.between(start,end).getSeconds()>86400){start=null;end=null;}}catch(DateTimeException|WorkforceException invalid){start=null;end=null;}}
   if(employeeRows.stream().anyMatch(old->old.storeId().equals(store)&&old.date().equals(date))){status="existing";reason="EXISTING_SCHEDULE";existing++;}
   else {
    if(date.isBefore(today)||date.isAfter(today.plusDays(90)))reason="OUTSIDE_WINDOW";
    else if(employee==null||!employee.status().equals("active")||date.isBefore(employee.hireDate())||employee.leaveDate()!=null&&date.isAfter(employee.leaveDate())||assignmentMap.getOrDefault(row.employeeId(),List.of()).stream().noneMatch(a->!date.isBefore(a.started())&&(a.ended()==null||date.isBefore(a.ended()))))reason="NOT_ASSIGNED";
    else if(shift==null||!shift.enabled())reason="SHIFT_UNAVAILABLE";
    else if(start==null||end==null)reason="INVALID_TIME";
    else {var interval=new Interval(start,end);if(employeeRows.stream().anyMatch(old->old.start().isBefore(interval.end())&&old.end().isAfter(interval.start())))reason="TIME_OVERLAP";else if(proposed.getOrDefault(row.employeeId(),List.of()).stream().anyMatch(old->old.start().isBefore(interval.end())&&old.end().isAfter(interval.start())))reason="CANDIDATE_OVERLAP";else proposed.computeIfAbsent(row.employeeId(),ignored->new ArrayList<>()).add(interval);}
    if(reason.isEmpty())ready++;else {status="conflict";conflicts++;}
   }
   items.add(new Item(row.id(),row.employeeId(),row.employeeNo(),row.employeeName(),row.workDate(),date,row.shiftId(),shift==null?row.shift().name():shift.name(),start,end,status,reason));
  }
  String fingerprint=BulkSchedulePlanService.hash(json.write(List.of("week-copy",request,timezone,source,shifts,states,assignments,occupied,items)));
  return new Preview(fingerprint,request.sourceWeek(),request.targetWeek(),employeeIds.size(),items.size(),ready,existing,conflicts,timezone,List.copyOf(items));
 }
}

