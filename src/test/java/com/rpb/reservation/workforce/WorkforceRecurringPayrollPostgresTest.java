package com.rpb.reservation.workforce;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkforceRecurringPayrollPostgresTest extends WorkforcePayrollPostgresTest {
 Rule generated(String mode){return new Rule("monthly","3000","8","44",false,"1.5",List.of(new Item("Fixed allowance","ow_allowance","200","full")),new Generation(mode,List.of(1,2,3,4,5)));}
 void fixed(){approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,generated("fixed"),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));}
 @Test void effectivePersonalPlanAutomaticallyGeneratesTwoMonthsButOneOffItemsDoNotCarry(){
  fixed();time.at("2026-12-02T01:00:00Z");var oct=create();assertThat(oct.batch().status()).isEqualTo("calculated");assertThat(oct.calculation().issues()).isEmpty();assertThat(oct.calculation().lines().getFirst().basicPay()).isEqualTo("3000.00");
  oct=approvedInput(admin,store,oct.batch().id(),employee,new InputWrite(0L,null,null,false,false,List.of(new Item("One off","ow_additional","100")),""));oct=action(oct,"calculate");assertThat(oct.calculation().totalGross()).isEqualTo("3300.00");oct=action(oct,"confirm");
  var nov=batches.create(admin,store,UUID.randomUUID(),new BatchWrite(company,"2026-11"));assertThat(nov.calculation().issues()).isEmpty();assertThat(nov.calculation().totalGross()).isEqualTo("3200.00");assertThat(nov.calculation().lines().getFirst().profile().id()).isEqualTo(profile);assertThat(nov.calculation().lines().getFirst().input().items()).isEmpty();assertThat(batches.detail(admin,store,oct.batch().id()).calculation().totalGross()).isEqualTo("3300.00");
 }
 @Test void freePayrollIgnoresUnapprovedAttendanceAndUnpaidLeave(){
  fixed();var shift=shifts.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(0L,"Missing","09:00","17:00",false,"none",0,0,0,0,false,true));var s=roster.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,employee,shift.id(),LocalDate.of(2026,10,28),"scheduled"));
  var type=UUID.randomUUID();jdbc.update("insert into workforce_leave_types(tenant_id,id,store_id,name,paid,enabled,version) values(?,?,?,?,false,true,1)",tenant,type,store,"Unpaid");
  jdbc.update("insert into workforce_leaves(id,tenant_id,store_id,schedule_id,employee_id,type_id,type_name,paid,start_at,end_at,reason,status,applicant_id,version) values(?,?,?,?,?,?,'Unpaid',false,?,?,'Leave','approved',?,1)",UUID.randomUUID(),tenant,store,s.id(),employee,type,java.sql.Timestamp.from(s.startAt()),java.sql.Timestamp.from(s.endAt()),employeeAccount);
  var b=create();assertThat(b.calculation().issues()).isEmpty();assertThat(b.calculation().lines().getFirst().sources()).isEmpty();assertThat(b.calculation().lines().getFirst().basicPay()).isEqualTo("3000.00");assertThat(b.calculation().lines().getFirst().overtimePay()).isEqualTo("0.00");
 }
 @Test void midMonthHireAndDepartureStillPayFullBasicAndFollowingMonthIsExcluded(){
  jdbc.update("update workforce_employees set hire_date='2026-10-15',company_effective_date='2026-10-15',leave_date='2026-10-20' where tenant_id=? and id=?",tenant,employee);jdbc.update("update workforce_company_history set effective_date='2026-10-15' where tenant_id=? and employee_id=?",tenant,employee);var installed=configs.findProfile(tenant,profile).orElseThrow();legacyProfile(new PayProfile(installed.id(),company,employee,installed.employeeName(),LocalDate.of(2026,10,15),template,installed.templateVersion(),generated("fixed"),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG",""),installed.version()+1));
  var b=create();assertThat(b.calculation().issues()).isEmpty();assertThat(b.calculation().lines().getFirst().basicPay()).isEqualTo("3000.00");time.at("2026-12-02T01:00:00Z");var n=batches.create(admin,store,UUID.randomUUID(),new BatchWrite(company,"2026-11"));assertThat(n.employees()).isEmpty();
 }
 @Test void attendanceModeNeverBypassesMissingSchedulesEvenWithManualReviewFlags(){
  approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,generated("attendance"),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
  var b=create();assertThat(b.calculation().issues()).contains("AUTO_SCHEDULE_MISSING");b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(0L,"22","22",true,true,List.of(),"Override"));b=action(b,"calculate");assertThat(b.calculation().issues()).contains("AUTO_SCHEDULE_MISSING");
 }
 @Test void createRetryDoesNotRegenerateOrChangeVersion(){fixed();var id=UUID.randomUUID();var first=batches.create(admin,store,id,new BatchWrite(company,"2026-10"));var second=batches.create(admin,store,id,new BatchWrite(company,"2026-10"));assertThat(second.batch()).isEqualTo(first.batch());assertThat(second.calculation()).isEqualTo(first.calculation());}
 @Test void midMonthRateChangeIsBlockedRatherThanUsingMonthEndRate(){fixed();approvedProfile(admin,store,UUID.randomUUID(),new ProfileWrite(0L,company,employee,LocalDate.of(2026,10,15),template,generated("fixed"),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));assertThat(create().calculation().issues()).contains("PROFILE_MULTIPLE");}
 @Test void normalMonthlyCalendarAutomaticallyGeneratesFullSalaryFromApprovedFacts(){
  approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,generated("attendance"),new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
  var shift=shifts.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(0L,"Working calendar","09:00","17:00",false,"none",0,0,0,0,false,true));
  for(var date:LocalDate.of(2026,10,1).datesUntil(LocalDate.of(2026,11,1)).filter(d->d.getDayOfWeek().getValue()<=5).toList()){
   time.at(date.atStartOfDay(ZoneId.of("Asia/Singapore")).toInstant().toString());
   var s=roster.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,employee,shift.id(),date,"scheduled"));time.at(s.startAt().toString());punches.punch(worker,store,UUID.randomUUID(),new com.rpb.reservation.workforce.attendance.AttendanceDtos.ClockWrite(s.id(),"IN"));time.at(s.endAt().toString());punches.punch(worker,store,UUID.randomUUID(),new com.rpb.reservation.workforce.attendance.AttendanceDtos.ClockWrite(s.id(),"OUT"));var row=approvals.detail(admin,store,s.id()).row();approvals.approve(admin,store,s.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Verified"));
  }
  time.at("2026-11-02T01:00:00Z");var b=create();assertThat(b.calculation().issues()).isEmpty();assertThat(b.calculation().lines().getFirst().input().workingDays()).isEqualTo("22");assertThat(b.calculation().lines().getFirst().input().payableDays()).isEqualTo("22");assertThat(b.calculation().totalGross()).isEqualTo("3200.00");assertThat(action(b,"confirm").batch().status()).isEqualTo("confirmed");
 }
 @Test void automaticGenerationRetainsPayrollPermissionsAndAppGate(){
  fixed();assertThatThrownBy(()->batches.create(worker,store,UUID.randomUUID(),new BatchWrite(company,"2026-10"))).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class);
  var foreignActor=new com.rpb.reservation.walkin.api.CurrentActor(UUID.randomUUID(),adminId,"staff",Set.of("tenant_admin"),admin.permissions(),Set.of(store));
  assertThatThrownBy(()->batches.create(foreignActor,store,UUID.randomUUID(),new BatchWrite(company,"2026-10"))).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class);
  for(var reason:List.of(com.rpb.reservation.appgate.domain.AppGateDenyReason.APP_DISABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.TENANT_APP_NOT_ENABLED,com.rpb.reservation.appgate.domain.AppGateDenyReason.STORE_APP_NOT_ENABLED)){
   org.mockito.Mockito.doAnswer(c->{com.rpb.reservation.appgate.application.AppGateAccessRequest request=c.getArgument(0);return com.rpb.reservation.appgate.domain.AppGateDecision.deny(request.appKey(),request.tenantId(),request.storeId(),request.requiredPermission(),reason);}).when(gate).evaluate(org.mockito.ArgumentMatchers.any());
   assertThatThrownBy(()->batches.create(admin,store,UUID.randomUUID(),new BatchWrite(company,"2026-10"))).isInstanceOf(com.rpb.reservation.workforce.domain.WorkforceException.class).extracting("code").isEqualTo(reason.name());
  }
 }
 @Test void automaticCreationRollsBackWhenAuditFails(){fixed();var id=UUID.randomUUID();org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable")).when(logs).append(org.mockito.ArgumentMatchers.any(com.rpb.reservation.common.scope.StoreScope.class),org.mockito.ArgumentMatchers.any());assertThatThrownBy(()->batches.create(admin,store,id,new BatchWrite(company,"2026-10"))).isInstanceOf(IllegalStateException.class);assertThat(payRepository.find(tenant,id)).isEmpty();}
 @Test void automaticSpecialDayReviewStillNeedsAnExplicitAdministratorNote(){
  var auto=new Rule("hourly","100","8","44",false,"1.5",List.of(),new Generation("attendance",List.of()));approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,LocalDate.of(2026,10,1),template,auto,new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("foreign",null,null,"GG","")));
  var s=completed();jdbc.update("insert into workforce_calendar_events(id,tenant_id,store_id,event_date,kind,name,enabled,version) values(?,?,?,?,'public_holiday','Special',true,1)",UUID.randomUUID(),tenant,store,s.workDate());
  var b=create();b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(0L,null,null,false,true,List.of(),""));b=action(b,"calculate");assertThat(b.calculation().issues()).contains("SPECIAL_DAYS_REVIEW");
 }
}
