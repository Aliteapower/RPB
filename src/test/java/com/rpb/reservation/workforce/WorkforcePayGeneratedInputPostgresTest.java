package com.rpb.reservation.workforce;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
class WorkforcePayGeneratedInputPostgresTest extends WorkforcePayrollPostgresTest {
 void plan(String mode,String basis){var old=configs.findProfile(tenant,profile).orElseThrow();approvedProfile(admin,store,profile,new ProfileWrite(old.version(),company,employee,old.effectiveFrom(),template,new Rule(basis,"3000","8","44",false,"1.5",List.of(),new Generation(mode,List.of(1,2,3,4,5))),old.cpf()));}
 void noteOnly(boolean fixed){plan(fixed?"fixed":"attendance",fixed?"monthly":"daily");if(!fixed)completed();var batch=create();var generated=batch.inputs().getFirst();var before=jdbc.queryForObject("select count(*) from workforce_pay_change_proofs where tenant_id=?",Integer.class,tenant);
  var write=new InputWrite(generated.version(),generated.workingDays(),generated.payableDays(),true,true,generated.items(),"Only reviewed generated facts");var saved=batches.input(admin,store,batch.batch().id(),employee,write);assertThat(saved.inputs().getFirst().note()).isEqualTo("Only reviewed generated facts");assertThat(jdbc.queryForObject("select count(*) from workforce_pay_change_proofs where tenant_id=?",Integer.class,tenant)).isEqualTo(before);
  assertThat(payRepository.inputs(tenant,batch.batch().id()).getFirst().payableDays()).isNull();assertThat(batches.input(admin,store,batch.batch().id(),employee,write)).isEqualTo(saved);
  var current=saved.inputs().getFirst();assertThatThrownBy(()->batches.input(admin,store,batch.batch().id(),employee,new InputWrite(current.version(),current.workingDays(),"0",true,true,List.of(),"Different unapproved units"))).hasMessage("WORKFORCE_PAYROLL_APPROVAL_REQUIRED");
 }
 @Test void fixedGeneratedUnitsCanBeReviewedWithoutAnotherAmountApproval(){noteOnly(true);}
 @Test void attendanceGeneratedUnitsCanBeReviewedWithoutAnotherAmountApproval(){noteOnly(false);}
 @Test void shiftEditingPreservesSavedScheduleAndNewFactsStillRequireTheirOwnHoursApproval(){
  var original=completed();var before=sources.capture(tenant,company,YearMonth.of(2026,10),List.of());
  shifts.save(admin,store,original.shiftId(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ShiftWrite(1L,"New unpaid break","09:00","17:00",false,"duration",60,480,0,0,false,true));
  assertThat(schedules.find(tenant,original.id()).orElseThrow().shift()).isEqualTo(original.shift());assertThat(sources.capture(tenant,company,YearMonth.of(2026,10),List.of()).fingerprint()).isEqualTo(before.fingerprint());
  assertThatThrownBy(()->roster.save(admin,store,original.id(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(1L,employee,original.shiftId(),original.workDate(),"scheduled"))).hasMessage("WORKFORCE_ACTIVE_ATTENDANCE");
  var next=roster.save(admin,store,UUID.randomUUID(),new com.rpb.reservation.workforce.scheduling.SchedulingDtos.ScheduleWrite(0L,employee,original.shiftId(),LocalDate.of(2026,10,29),"scheduled"));assertThat(sources.capture(tenant,company,YearMonth.of(2026,10),List.of()).lines().getFirst().issues()).contains("APPROVAL_MISSING");
  time.at(next.startAt().toString());punches.punch(worker,store,UUID.randomUUID(),new com.rpb.reservation.workforce.attendance.AttendanceDtos.ClockWrite(next.id(),"IN"));time.at(next.endAt().toString());punches.punch(worker,store,UUID.randomUUID(),new com.rpb.reservation.workforce.attendance.AttendanceDtos.ClockWrite(next.id(),"OUT"));time.at(next.endAt().plusSeconds(1).toString());var row=approvals.detail(admin,store,next.id()).row();approvals.approve(admin,store,next.id(),new com.rpb.reservation.workforce.approval.ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"New shift facts checked"));
  var calculation=action(create(),"calculate").calculation();assertThat(calculation.totalGross()).isEqualTo("1500.00");assertThat(calculation.lines().getFirst().profile().rule().rate()).isEqualTo("100.00");assertThat(jdbc.queryForObject("select count(*) from workforce_pay_change_requests where tenant_id=?",Integer.class,tenant)).isZero();
 }
}
