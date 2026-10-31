package com.rpb.reservation.workforce.scheduling.bulk;
import static com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleDtos.*;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class BulkSchedulePlanService {
 private final WorkforceAccess access;private final BulkScheduleRepository repository;private final SchedulingRepository schedules;private final WorkforceJson json;private final Clock clock;
 public BulkSchedulePlanService(WorkforceAccess access,BulkScheduleRepository repository,SchedulingRepository schedules,WorkforceJson json,Clock clock){this.access=access;this.repository=repository;this.schedules=schedules;this.json=json;this.clock=clock;}
 public static Request normalize(Request c){
  if(c==null||c.employeeIds()==null||c.employeeIds().isEmpty()||c.employeeIds().size()>500||c.employeeIds().stream().anyMatch(Objects::isNull)||c.shiftId()==null||c.weekdays()==null||c.weekdays().isEmpty()||c.weekdays().size()>7||c.weekdays().stream().anyMatch(d->d==null||d<1||d>7))throw WorkforceException.invalid();
  ScheduleService.range(c.from(),c.to());var ids=c.employeeIds().stream().distinct().sorted().toList();var days=c.weekdays().stream().distinct().sorted().toList();
  long dates=c.from().datesUntil(c.to().plusDays(1)).filter(d->days.contains(d.getDayOfWeek().getValue())).count();if(dates==0||dates*ids.size()>5000)throw WorkforceException.invalid();
  return new Request(ids,c.shiftId(),c.from(),c.to(),days);
 }
 private void manage(CurrentActor actor,UUID store){access.enter(actor,store);access.manage(actor,store);}
 private String search(String search,int limit,int offset){if(limit<1||limit>100||offset<0||search!=null&&search.length()>100)throw WorkforceException.invalid();return search==null?"":search.trim();}
 public Options options(CurrentActor actor,UUID store,UUID position,String search,int limit,int offset){manage(actor,store);var page=repository.page(actor.tenantId(),store,position,search(search,limit,offset),limit,offset,null);return new Options(page.items(),page.total(),limit,offset,repository.positions(actor.tenantId()),schedules.shifts(actor.tenantId(),store),schedules.timezone(actor.tenantId(),store));}
 public Board board(CurrentActor actor,UUID store,LocalDate from,UUID position,String search,int limit,int offset){
  manage(actor,store);if(from==null||from.getDayOfWeek()!=DayOfWeek.MONDAY)throw WorkforceException.invalid();return boardRange(actor,store,from,from.plusDays(6),position,search,limit,offset);
 }
 public Board monthBoard(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID position,String search,int limit,int offset){
  manage(actor,store);if(from==null||to==null||from.getDayOfMonth()!=1||!to.equals(from.withDayOfMonth(from.lengthOfMonth())))throw WorkforceException.invalid();return boardRange(actor,store,from,to,position,search,limit,offset);
 }
 private Board boardRange(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID position,String search,int limit,int offset){
  var page=repository.page(actor.tenantId(),store,position,search(search,limit,offset),limit,offset,from,to);
  var monday=from.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));long weekCount=repository.weekCount(actor.tenantId(),store,monday);
  return new Board(page.items(),page.total(),limit,offset,schedules.forEmployees(actor.tenantId(),store,from,to,page.items().stream().map(Employee::id).toList()),schedules.timezone(actor.tenantId(),store),weekCount,repository.positions(actor.tenantId()),to.equals(monday.plusDays(6))&&from.equals(monday)?weekCount:repository.periodCount(actor.tenantId(),store,from,to),repository.staffing(actor.tenantId(),store,from,to,position,search==null?"":search.trim()),repository.calendar(actor.tenantId(),store,from,to),repository.restSwaps(actor.tenantId(),store,from,to,page.items().stream().map(Employee::id).toList()));
 }
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public StaffingEmployees staffingEmployees(CurrentActor actor,UUID store,LocalDate from,LocalDate to,UUID shift,UUID position,String search,int limit,int offset){
  manage(actor,store);ScheduleService.range(from,to);if(shift==null)throw WorkforceException.invalid();return repository.staffingEmployees(actor.tenantId(),store,from,to,shift,position,search(search,limit,offset),limit,offset);
 }
 public Preview preview(CurrentActor actor,UUID store,Request input){
  manage(actor,store);var c=normalize(input);String timezone=schedules.timezone(actor.tenantId(),store);var zone=ZoneId.of(timezone);var today=LocalDate.now(clock.withZone(zone));if(c.from().isBefore(today)||c.to().isAfter(today.plusDays(90)))throw WorkforceException.invalid();
  var shift=schedules.shift(actor.tenantId(),store,c.shiftId()).orElseThrow(WorkforceException::missing);if(!shift.enabled())throw WorkforceException.invalid();
  var states=repository.states(actor.tenantId(),c.employeeIds());if(states.size()!=c.employeeIds().size())throw WorkforceException.missing();var assignments=repository.assignments(actor.tenantId(),store,c.employeeIds());
  if(states.stream().anyMatch(e->assignments.stream().noneMatch(a->a.employeeId().equals(e.id()))))throw WorkforceException.missing();
  var occupied=repository.occupied(actor.tenantId(),c.employeeIds(),c.from(),c.to(),c.from().atStartOfDay(zone).toInstant(),c.to().plusDays(3).atStartOfDay(zone).toInstant());
  var rows=new ArrayList<Item>();int ready=0,existing=0,conflicts=0;
  for(var e:states){var employeeRows=occupied.stream().filter(s->s.employeeId().equals(e.id())).toList();var employeeAssignments=assignments.stream().filter(a->a.employeeId().equals(e.id())).toList();
   for(var date=c.from();!date.isAfter(c.to());date=date.plusDays(1)){
    if(!c.weekdays().contains(date.getDayOfWeek().getValue()))continue;var day=date;String status="ready",reason="";
    var interval=ShiftTiming.resolve(day,shift,zone);Instant start=interval.start(),end=interval.end();if(!end.isAfter(start)||Duration.between(start,end).getSeconds()>86400)throw WorkforceException.invalid();
    if(employeeRows.stream().anyMatch(s->s.storeId().equals(store)&&s.date().equals(day))){status="existing";reason="EXISTING_SCHEDULE";existing++;}
    else if(!e.status().equals("active")||day.isBefore(e.hireDate())||e.leaveDate()!=null&&day.isAfter(e.leaveDate())||employeeAssignments.stream().noneMatch(a->!day.isBefore(a.started())&&(a.ended()==null||day.isBefore(a.ended())))){status="conflict";reason="NOT_ASSIGNED";conflicts++;}
    else if(employeeRows.stream().anyMatch(s->s.start().isBefore(end)&&s.end().isAfter(start))){status="conflict";reason="TIME_OVERLAP";conflicts++;}else ready++;
    rows.add(new Item(e.id(),e.employeeNo(),e.name(),day,status,reason));
   }
  }
  return new Preview(hash(json.write(List.of(c,timezone,shift,states,assignments,occupied))),states.size(),rows.size(),ready,existing,conflicts,List.copyOf(rows),timezone);
 }
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}


