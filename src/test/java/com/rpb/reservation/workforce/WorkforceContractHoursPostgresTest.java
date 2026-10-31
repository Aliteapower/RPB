package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.workforce.calendar.CalendarRepository;
import com.rpb.reservation.workforce.compliance.*;
import com.rpb.reservation.workforce.leave.LeaveRepository;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkforceContractHoursPostgresTest extends WorkforceLeaveAccountPostgresTest {
    record Setup(Schedule schedule,ApprovalSources reader) {}
    Setup completedOutsideRoster() {
        setupAccounts("2","14","60");
        var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());
        var reader=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,time);
        reader.compliance(new ComplianceEvidence(policies));sources.compliance(new ComplianceEvidence(policies));
        var ar=new ApprovalRepository(jdbc,json);
        approvals=proxy(new ApprovalService(access,new AttendanceAccess(access,repository,schedules),repository,schedules,ar,reader,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),time));
        sources=new PayrollSources(payRepository,configs,repository,schedules,ar,reader,json);sources.compliance(new ComplianceEvidence(policies));
        batches=proxy(new PayrollBatchService(access,new PayrollAccess(access,directory,jdbc),payRepository,sources,new PayrollCalculationService(time),repository,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),time,tx));
        var r=new PayrollDtos.Rule("hourly","10","8","44",false,"1.5",List.of());
        legacyProfile(new PayrollDtos.PayProfile(UUID.randomUUID(),company,employee,"Synthetic",LocalDate.of(2026,11,1),template,1,r,new CpfProfile("foreign",LocalDate.of(1990,1,1),null,"GG",""),1));
        var shift=shifts.save(admin,store,UUID.randomUUID(),new ShiftWrite(0L,"Actual break day","09:00","17:00",false,"manual",60,0,0,0,false,true));
        var s=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),LocalDate.of(2026,11,2),"scheduled"));
        String[] at={"2026-11-02T01:00:00Z","2026-11-02T04:00:00Z","2026-11-02T05:00:00Z","2026-11-02T11:00:00Z"};
        String[] actions={"IN","BREAK_START","BREAK_END","OUT"};
        for(int i=0;i<at.length;i++){time.at(at[i]);punches.punch(worker,store,UUID.randomUUID(),new AttendanceDtos.ClockWrite(s.id(),actions[i]));}
        time.at("2026-11-02T12:00:00Z");return new Setup(s,reader);
    }
    @Test void outsideRosterRequiresReasonedReviewAndMandatoryPremiumSurvivesDisabledLegacyFlag() {
        var setup=completedOutsideRoster();var s=setup.schedule();var row=approvals.detail(admin,store,s.id()).row();
        assertThat(row.findings()).anyMatch(f->f.code().equals("OUTSIDE_SCHEDULE")&&f.status().equals("pending"));
        assertThatThrownBy(()->approvals.approve(admin,store,s.id(),new ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Checked"))).hasMessage("WORKFORCE_APPROVAL_BLOCKED");
        approvals.approve(admin,store,s.id(),new ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Time verified",List.of(new ApprovalDtos.ReviewDecision("OUTSIDE_SCHEDULE","confirmed","Actual work verified against supervisor task record"))));
        assertThat(attendance.events(tenant,s.id())).hasSize(4);
        jdbc.update("update workforce_employees set leave_date='2026-10-31' where tenant_id=? and id<>?",tenant,employee);
        time.at("2026-12-02T00:00:00Z");var b=batches.create(admin,store,UUID.randomUUID(),new PayrollDtos.BatchWrite(company,"2026-11"));b=action(b,"calculate");
        assertThat(b.calculation().issues()).isEmpty();var line=b.calculation().lines().getFirst();assertThat(line.basicPay()).isEqualTo("80.00");assertThat(line.overtimePay()).isEqualTo("15.00");assertThat(line.grossPay()).isEqualTo("95.00");assertThat(line.compliance().contract().weeklyHours()).isEqualByComparingTo("40");
        b=action(b,"confirm");String frozen=jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,b.batch().id());
        var old=policies.policies(tenant).getFirst();compliance.savePolicy(admin,store,old.id(),new ComplianceDtos.PolicyWrite(old.version(),"Next policy version",LocalDate.of(2027,1,1),LocalDate.of(2028,12,31),true,old.rules(),old.sources()));
        assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,b.batch().id())).isEqualTo(frozen);
    }
    @Test void nonWorkPresenceCannotSilentlyEraseActualWorkAndUsesExistingCorrectionApproval() {
        var setup=completedOutsideRoster();var s=setup.schedule();var row=approvals.detail(admin,store,s.id()).row();
        assertThatThrownBy(()->approvals.approve(admin,store,s.id(),new ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Non-work presence",List.of(new ApprovalDtos.ReviewDecision("OUTSIDE_SCHEDULE","non_work","Verified personal presence, effective time requires correction"))))).hasMessage("WORKFORCE_APPROVAL_BLOCKED");
        var session=attendance.bySchedule(tenant,s.id()).orElseThrow();assertThat(session.outAt()).isEqualTo(Instant.parse("2026-11-02T11:00:00Z"));
        var correction=corrections.submit(worker,store,UUID.randomUUID(),new AttendanceDtos.CorrectionWrite(s.id(),session.version(),s.startAt(),s.endAt(),session.breaks(),false,"Actual work ended at roster end; later presence was personal"));
        corrections.decide(admin,store,correction.id(),new AttendanceDtos.DecisionWrite(1L,"approved","Supervisor evidence independently verified"));
        var refreshed=approvals.detail(admin,store,s.id()).row();assertThat(refreshed.workSeconds()).isEqualTo(7*3600);assertThat(refreshed.findings()==null?List.<ApprovalDtos.Finding>of():refreshed.findings()).noneMatch(f->f.code().equals("OUTSIDE_SCHEDULE"));assertThat(attendance.events(tenant,s.id())).hasSize(4);
        assertThat(attendance.events(tenant,s.id())).anyMatch(e->e.action().equals("OUT")&&e.occurredAt().equals(Instant.parse("2026-11-02T11:00:00Z")));
    }

    @Test void mandatoryOvertimeLoadsPriorMonthSameWeekEvenWhenLegacyFlagIsOff(){
        setupAccounts("2","14","60");var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var reader=new ApprovalSources(attendance,new LeaveRepository(jdbc),new CalendarRepository(jdbc),json,time);reader.compliance(new ComplianceEvidence(policies));var ar=new ApprovalRepository(jdbc,json);approvals=proxy(new ApprovalService(access,new AttendanceAccess(access,repository,schedules),repository,schedules,ar,reader,new WorkforceAudit(logs,new ObjectMapper().findAndRegisterModules()),time));sources=new PayrollSources(payRepository,configs,repository,schedules,ar,reader,json);sources.compliance(new ComplianceEvidence(policies));var r=new PayrollDtos.Rule("hourly","10","8","44",false,"1.5",List.of());legacyProfile(new PayrollDtos.PayProfile(UUID.randomUUID(),company,employee,"Synthetic",LocalDate.of(2026,10,1),template,1,r,new CpfProfile("foreign",LocalDate.of(1990,1,1),null,"GG",""),1));var shift=shifts.save(admin,store,UUID.randomUUID(),new ShiftWrite(0L,"Cross-month week","09:00","18:00",false,"manual",60,0,0,0,false,true));time.at("2026-11-03T00:00:00Z");
        for(var date=LocalDate.of(2026,10,26);!date.isAfter(LocalDate.of(2026,11,1));date=date.plusDays(1)){time.at(date.toString()+"T00:00:00Z");var s=roster.save(admin,store,UUID.randomUUID(),new ScheduleWrite(0L,employee,shift.id(),date,"scheduled"));attendance.save(tenant,new AttendanceDtos.Session(UUID.randomUUID(),s.id(),employee,store,s.startAt(),s.endAt(),null,List.of(new AttendanceDtos.Interval(s.startAt().plusSeconds(3*3600),s.startAt().plusSeconds(4*3600))),false,1));time.at(date.toString()+"T12:00:00Z");var row=approvals.detail(admin,store,s.id()).row();approvals.approve(admin,store,s.id(),new ApprovalDtos.ApprovalWrite(0L,row.sourceFingerprint(),"Cross-month week actual work verified"));}
        jdbc.update("update workforce_employees set leave_date='2026-10-31' where tenant_id=? and id<>?",tenant,employee);var capture=sources.capture(tenant,company,YearMonth.of(2026,11),List.of());assertThat(capture.lines().getFirst().sources().stream().filter(ref->!ref.payable())).hasSize(6);var result=new PayrollCalculationService(time).calculate(capture,YearMonth.of(2026,11));assertThat(result.issues()).isEmpty();assertThat(result.lines().getFirst().overtimeSeconds()).isEqualTo(8*3600);assertThat(result.totalGross()).isEqualTo("120.00");
    }
}
