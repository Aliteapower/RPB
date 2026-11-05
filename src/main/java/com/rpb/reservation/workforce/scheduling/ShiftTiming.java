package com.rpb.reservation.workforce.scheduling;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Shift;
import java.time.*;
/** Actual calendar times are offsets from the immutable roster business date. */
public final class ShiftTiming {
 private ShiftTiming(){}
 public record Interval(Instant start,Instant end){}
 public static Interval resolve(LocalDate businessDate,Shift shift,ZoneId zone){
  LocalDate actualStart=businessDate.plusDays(Boolean.TRUE.equals(shift.startNextDay())?1:0);
  Instant start=instant(actualStart,shift.startTime(),zone),end=instant(actualStart.plusDays(shift.nextDay()?1:0),shift.endTime(),zone);
  if(!end.isAfter(start)||Duration.between(start,end).getSeconds()>86400)throw WorkforceException.invalid();
  return new Interval(start,end);
 }
 private static Instant instant(LocalDate date,String time,ZoneId zone){
  var local=LocalDateTime.of(date,LocalTime.parse(time));var offsets=zone.getRules().getValidOffsets(local);
  if(offsets.size()!=1)throw WorkforceException.invalid();return local.toInstant(offsets.getFirst());
 }
}
