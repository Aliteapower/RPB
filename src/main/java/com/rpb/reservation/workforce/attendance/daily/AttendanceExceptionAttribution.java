package com.rpb.reservation.workforce.attendance.daily;
import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;
/** Validates the explicit business day without treating local midnight as a shift boundary. */
public final class AttendanceExceptionAttribution {
 private AttendanceExceptionAttribution(){}
 public record Window(LocalDate workDate,Instant start,Instant end){}
 public static void validate(ExceptionWrite c,ZoneId zone,List<Window> windows){
  var own=windows.stream().filter(w->w.workDate().equals(c.workDate())).toList();
  var startDate=c.startAt().atZone(zone).toLocalDate();
  if(own.isEmpty()){
   Instant end=c.workDate().plusDays(1).atStartOfDay(zone).toInstant();
   if(c.kind().equals("extra_work")){end=c.startAt().plus(Duration.ofHours(24));var limit=c.workDate().plusDays(2).atStartOfDay(zone).toInstant();if(end.isAfter(limit))end=limit;for(var other:windows)if(other.workDate().isAfter(c.workDate())&&other.start().isBefore(end))end=other.start();}
   if(!startDate.equals(c.workDate())||c.endAt().isAfter(end))throw WorkforceException.invalid();
   rejectOtherDayOverlap(c,windows);return;
  }
  Instant first=own.stream().map(Window::start).min(Comparator.naturalOrder()).orElseThrow(),last=own.stream().map(Window::end).max(Comparator.naturalOrder()).orElseThrow();
  if(!c.kind().equals("extra_work")){
   if(c.startAt().isBefore(first)||c.endAt().isAfter(last))throw WorkforceException.invalid();
  }else{
   // An after-midnight tail belongs to yesterday only when it directly follows its overnight shift.
   if(!startDate.equals(c.workDate())&&!(startDate.equals(c.workDate().plusDays(1))&&last.atZone(zone).toLocalDate().equals(startDate)&&c.startAt().equals(last)))throw WorkforceException.invalid();
   Instant end=first.atZone(zone).plusDays(1).toInstant();
   for(var other:windows)if(other.workDate().isAfter(c.workDate())&&!other.start().isBefore(last)&&other.start().isBefore(end))end=other.start();
   if(c.endAt().isAfter(end))throw WorkforceException.invalid();
  }
  rejectOtherDayOverlap(c,windows);
 }
 private static void rejectOtherDayOverlap(ExceptionWrite c,List<Window> windows){if(windows.stream().anyMatch(w->!w.workDate().equals(c.workDate())&&c.startAt().isBefore(w.end())&&c.endAt().isAfter(w.start())))throw WorkforceException.invalid();}
}
