package com.rpb.reservation.workforce;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Shift;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ShiftTimingTest {
 private Shift shift(Boolean startNext,boolean endNext,String start,String end){return new Shift(UUID.randomUUID(),"Night",start,end,endNext,"none",0,0,0,0,false,true,1,null,startNext);}
 @Test void absentOffsetPreservesOldJsonAndTrueOffsetRoundTrips()throws Exception{
  var mapper=new ObjectMapper();var old=shift(null,true,"23:00","06:00");var serialized=mapper.writeValueAsString(old);assertThat(serialized).doesNotContain("startNextDay");assertThat(mapper.readValue(serialized,Shift.class)).isEqualTo(old);
  var shifted=shift(true,false,"01:00","06:00");assertThat(mapper.readValue(mapper.writeValueAsString(shifted),Shift.class)).isEqualTo(shifted);
 }
 @Test void actualDayOffsetsRemainSeparateFromBusinessDate(){
  var day=LocalDate.of(2026,10,31);var zone=ZoneId.of("Asia/Singapore");var same=ShiftTiming.resolve(day,shift(null,true,"23:00","06:00"),zone);var next=ShiftTiming.resolve(day,shift(true,false,"01:00","06:00"),zone);var second=ShiftTiming.resolve(day,shift(true,true,"23:00","06:00"),zone);
  assertThat(same.start().atZone(zone).toLocalDate()).isEqualTo(day);assertThat(same.end().atZone(zone).toLocalDate()).isEqualTo(day.plusDays(1));assertThat(next.start().atZone(zone).toLocalDate()).isEqualTo(day.plusDays(1));assertThat(Duration.between(next.start(),next.end()).toHours()).isEqualTo(5);assertThat(second.end().atZone(zone).toLocalDate()).isEqualTo(day.plusDays(2));
 }
 @Test void invalidDstAndOverlongIntervalsKeepExistingRejection(){
  assertThatThrownBy(()->ShiftTiming.resolve(LocalDate.of(2026,3,7),shift(true,false,"02:30","06:00"),ZoneId.of("America/New_York"))).hasMessage("WORKFORCE_INVALID");
  assertThatThrownBy(()->ShiftTiming.resolve(LocalDate.of(2026,10,31),shift(true,false,"01:30","06:00"),ZoneId.of("America/New_York"))).hasMessage("WORKFORCE_INVALID");
  assertThatThrownBy(()->ShiftTiming.resolve(LocalDate.of(2026,10,31),shift(true,true,"01:00","06:00"),ZoneId.of("Asia/Singapore"))).hasMessage("WORKFORCE_INVALID");
 }
}
