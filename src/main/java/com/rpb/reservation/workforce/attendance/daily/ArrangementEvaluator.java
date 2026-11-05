package com.rpb.reservation.workforce.attendance.daily;
import java.time.*;import java.util.*;import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;
/** Exact interval facts; no inferred break window, paid amount or overtime entitlement. */
public final class ArrangementEvaluator {
 public record Result(Long workSeconds,long paidLeaveSeconds,long unpaidLeaveSeconds,long absenceSeconds,long extraSeconds,List<String> issues){}
 public Result evaluate(Instant start,Instant end,long standard,long breaks,Instant now,List<IntervalException> exceptions){
  return evaluate(start,end,standard,breaks,null,null,now,exceptions);
 }
 public Result evaluate(Instant start,Instant end,long standard,long breaks,Instant breakStart,Instant breakEnd,Instant now,List<IntervalException> exceptions){
  var windows=breakStart==null||breakEnd==null?List.<com.rpb.reservation.workforce.attendance.AttendanceDtos.Interval>of():List.of(new com.rpb.reservation.workforce.attendance.AttendanceDtos.Interval(breakStart,breakEnd));return evaluate(start,end,standard,breaks,windows,now,exceptions);
 }
 public Result evaluate(Instant start,Instant end,long standard,long breaks,List<com.rpb.reservation.workforce.attendance.AttendanceDtos.Interval> windows,Instant now,List<IntervalException> exceptions){
  var issues=new LinkedHashSet<String>();if(now.isBefore(end))issues.add("SHIFT_NOT_ENDED");long paid=0,unpaid=0,absence=0,extra=0,deduct=0;Instant previous=null;
  var approved=exceptions.stream().filter(e->Set.of("approved","cancel_requested").contains(e.status())).sorted(Comparator.comparing(IntervalException::startAt)).toList();
  if(exceptions.stream().anyMatch(e->Set.of("pending","cancel_requested").contains(e.status())))issues.add("PENDING_REQUEST");
  for(var e:approved){if(previous!=null&&e.startAt().isBefore(previous))issues.add("EXCEPTION_OVERLAP");previous=e.endAt();if(e.kind().equals("extra_work")){if(now.isBefore(e.endAt()))issues.add("EXTRA_WORK_NOT_ENDED");if(e.startAt().isBefore(end)&&e.endAt().isAfter(start))issues.add("EXTRA_WORK_OVERLAP");extra+=Duration.between(e.startAt(),e.endAt()).getSeconds();continue;}
   var a=e.startAt().isAfter(start)?e.startAt():start;var b=e.endAt().isBefore(end)?e.endAt():end;if(!b.isAfter(a))continue;long seconds=Duration.between(a,b).getSeconds();if(breaks>0){if(a.equals(start)&&b.equals(end))seconds=standard;else if(!windows.isEmpty()){seconds=com.rpb.reservation.workforce.attendance.BreakCalculator.subtract(a,b,windows).stream().mapToLong(i->Duration.between(i.start(),i.end()).getSeconds()).sum();}else issues.add("BREAK_WINDOW_REQUIRED");}deduct+=seconds;if(e.kind().equals("absence"))absence+=seconds;else if(e.paid())paid+=seconds;else unpaid+=seconds;
  }
  if(deduct>standard)issues.add("EXCEPTION_OVERLAP");return new Result(issues.isEmpty()?Math.max(0,standard-deduct)+extra:null,paid,unpaid,absence,extra,List.copyOf(issues));
 }
}
