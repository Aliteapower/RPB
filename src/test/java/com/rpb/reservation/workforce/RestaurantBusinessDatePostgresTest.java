package com.rpb.reservation.workforce;
import static com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
/** Restaurant business-date regressions against the owning pointer PG in an isolated schema. */
class RestaurantBusinessDatePostgresTest extends WorkforcePayrollPostgresTest {
 @Test void nextDayStartAndFollowingDayEndStayDiscoverableAndBulkPreviewDetectsDayTwoConflicts()throws Exception{
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var json=new com.rpb.reservation.workforce.application.WorkforceJson(mapper.findAndRegisterModules());var write=mapper.readValue("""
   {"expectedVersion":0,"name":"次日夜班","startTime":"23:00","endTime":"06:00","nextDay":true,"startNextDay":true,"breakMode":"none","breakMinutes":0,"breakAfterMinutes":0,"breakStartOffset":0,"breakEndOffset":0,"breakPaid":false,"enabled":true}
   """,ShiftWrite.class);var shift=shifts.save(admin,store,UUID.randomUUID(),write);var day=LocalDate.of(2026,10,31);
  var early=restaurant("第二日冲突","05:00","13:00",false);roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,early.id(),day.plusDays(2),"scheduled"));
  var bulk=new com.rpb.reservation.workforce.scheduling.bulk.BulkSchedulePlanService(access,new com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleRepository(jdbc,json),schedules,json,time);
  var request=new com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleDtos.Request(List.of(employee),shift.id(),day,day,List.of(day.getDayOfWeek().getValue()));var preview=bulk.preview(admin,store,request);assertThat(preview.items()).singleElement().satisfies(item->assertThat(item.reason()).isEqualTo("TIME_OVERLAP"));
  var clean=day.plusDays(3);var s=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),clean,"scheduled"));assertThat(s.startAt().atZone(ZoneId.of("Asia/Singapore")).toLocalDate()).isEqualTo(clean.plusDays(1));assertThat(s.endAt().atZone(ZoneId.of("Asia/Singapore")).toLocalDate()).isEqualTo(clean.plusDays(2));
  time.at(s.endAt().minusSeconds(3600).toString());var query=new com.rpb.reservation.workforce.attendance.AttendanceQueryService(access,new com.rpb.reservation.workforce.attendance.AttendanceAccess(access,repository,schedules),schedules,attendance,time);assertThat(query.home(worker,store).schedules()).extracting(Schedule::id).contains(s.id());
 }
 @Test void nextDayStartKeepsOriginalBusinessDateForClocksAndPayroll()throws Exception{
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var write=mapper.readValue("""
   {"expectedVersion":0,"name":"次日凌晨班","startTime":"01:00","endTime":"06:00","nextDay":false,"startNextDay":true,"breakMode":"none","breakMinutes":0,"breakAfterMinutes":0,"breakStartOffset":0,"breakEndOffset":0,"breakPaid":false,"enabled":true}
   """,ShiftWrite.class);var shift=shifts.save(admin,store,UUID.randomUUID(),write);var businessDate=LocalDate.of(2026,10,31);var plan=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),businessDate,"scheduled"));
  assertThat(plan.startAt()).isEqualTo(Instant.parse("2026-10-31T17:00:00Z"));assertThat(plan.endAt()).isEqualTo(Instant.parse("2026-10-31T22:00:00Z"));assertThat(plan.workDate()).isEqualTo(businessDate);
  time.at(plan.startAt().toString());var query=new com.rpb.reservation.workforce.attendance.AttendanceQueryService(access,new com.rpb.reservation.workforce.attendance.AttendanceAccess(access,repository,schedules),schedules,attendance,time);assertThat(query.home(worker,store).schedules()).extracting(Schedule::id).contains(plan.id());
  punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(plan.id(),"IN"));time.at(plan.endAt().toString());punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(plan.id(),"OUT"));var row=approvals.detail(admin,store,plan.id()).row();approvals.approve(admin,store,plan.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Next-day start verified"));
  time.at("2026-11-02T01:00:00Z");var oct=sources.capture(tenant,company,YearMonth.of(2026,10),List.of());assertThat(oct.lines().getFirst().sources()).singleElement().satisfies(ref->{assertThat(ref.workDate()).isEqualTo(businessDate);assertThat(ref.workSeconds()).isEqualTo(18000);});assertThat(sources.capture(tenant,company,YearMonth.of(2026,11),List.of()).lines().getFirst().sources()).isEmpty();
 }
 Shift restaurant(String name,String start,String end,boolean next){return shifts.save(admin,store,UUID.randomUUID(),new ShiftWrite(0L,name,start,end,next,"none",0,0,0,0,false,true));}
 @Test void earlyMiddleAndLateRetainTheirBusinessDateAndExactElapsedTime(){
  var day=LocalDate.of(2026,10,31);
  var early=restaurant("早班","11:00","23:00",false);var middle=restaurant("中班","16:00","00:00",true);var late=restaurant("晚班","23:00","06:00",true);
  var a=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,early.id(),day,"scheduled"));assertThat(Duration.between(a.startAt(),a.endAt()).toHours()).isEqualTo(12);
  roster.save(admin,store,a.id(),new ScheduleWrite(a.version(),employee,early.id(),day,"cancelled"));
  var b=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,middle.id(),day,"scheduled"));assertThat(b.endAt().atZone(ZoneId.of("Asia/Singapore")).toLocalDate()).isEqualTo(day.plusDays(1));assertThat(Duration.between(b.startAt(),b.endAt()).toHours()).isEqualTo(8);
  roster.save(admin,store,b.id(),new ScheduleWrite(b.version(),employee,middle.id(),day,"cancelled"));
  var c=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,late.id(),day,"scheduled"));assertThat(c.workDate()).isEqualTo(day);assertThat(Duration.between(c.startAt(),c.endAt()).toHours()).isEqualTo(7);
 }
 @Test void overnightClockApprovalAndMonthlyPayrollUseBusinessDateWithoutDoublePayment(){
  var day=LocalDate.of(2026,10,31);var late=restaurant("晚班","23:00","06:00",true);var s=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,late.id(),day,"scheduled"));
  time.at(s.startAt().toString());punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(s.id(),"IN"));time.at(s.endAt().toString());punches.punch(worker,store,UUID.randomUUID(),new ClockWrite(s.id(),"OUT"));
  var row=approvals.detail(admin,store,s.id()).row();assertThat(row.workSeconds()).isEqualTo(7*3600L);approvals.approve(admin,store,s.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Night business date checked"));
  time.at("2026-11-02T01:00:00Z");var oct=create();oct=approvedInput(admin,store,oct.batch().id(),employee,new InputWrite(0L,null,null,true,false,List.of(),"Verified overnight work"));oct=action(oct,"calculate");assertThat(oct.calculation().issues()).isEmpty();assertThat(oct.calculation().lines().getFirst().workSeconds()).isEqualTo(7*3600);assertThat(oct.calculation().totalGross()).isEqualTo("700.00");assertThat(oct.calculation().lines().getFirst().sources()).singleElement().satisfies(ref->assertThat(ref.workDate()).isEqualTo(day));
  var november=sources.capture(tenant,company,YearMonth.of(2026,11),List.of());assertThat(november.lines().getFirst().sources()).isEmpty();
  oct=action(oct,"confirm");var frozen=oct.calculation();shifts.save(admin,store,late.id(),new ShiftWrite(1L,"晚班调整","23:00","05:00",true,"none",0,0,0,0,false,true));
  assertThat(schedules.find(tenant,s.id()).orElseThrow().shift().endTime()).isEqualTo("06:00");assertThat(batches.detail(admin,store,oct.batch().id()).calculation()).isEqualTo(frozen);
 }
 @Test void overnightConflictChecksActualInstantsAcrossBusinessDates(){
  var day=LocalDate.of(2026,10,31);var late=restaurant("晚班","23:00","06:00",true);roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,late.id(),day,"scheduled"));
  var overlap=restaurant("重叠早班","05:00","13:00",false);assertThatThrownBy(()->roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,overlap.id(),day.plusDays(1),"scheduled"))).hasMessage("WORKFORCE_SCHEDULE_OVERLAP");
  var adjacent=restaurant("相邻早班","06:00","14:00",false);assertThat(roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,adjacent.id(),day.plusDays(1),"scheduled"))).isNotNull();
 }
}

