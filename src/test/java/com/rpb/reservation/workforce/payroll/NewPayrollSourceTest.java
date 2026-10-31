package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewPayrollSourceTest {
    @Test void segmentCaptureStopsAtCutoffAndRetainsAttendanceBonusEvidence() {
        var daily=mock(com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService.class);
        var tenant=UUID.randomUUID();var company=UUID.randomUUID();var employee=UUID.randomUUID();var store=UUID.randomUUID();
        var month=java.time.YearMonth.of(2026,10);var cutoff=month.atDay(15);
        var day=new com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.EffectiveDay(UUID.randomUUID(),store,cutoff,"default_normal","system_valid","arrangement","c".repeat(64),3,null,28800L,0,0,0,java.util.Map.of("fullAttendanceStatus","eligible"),List.of());
        when(daily.capture(eq(tenant),eq(company),eq(employee),any(),eq(cutoff))).thenReturn(List.of(day));
        var rule=new Rule("hourly","20","8","44",false,"1.5",List.of(),new Generation("attendance",List.of()));
        var profile=new PayProfile(UUID.randomUUID(),company,employee,"Test",month.atDay(1),UUID.randomUUID(),1,rule,null,1);
        var captured=new AttendancePayrollSources(daily).capture(tenant,company,employee,month,profile,cutoff);
        verify(daily).capture(tenant,company,employee,month.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),cutoff);
        assertThat(captured.sources()).singleElement().satisfies(ref->{assertThat(ref.evidence().fullAttendanceStatus()).isEqualTo("eligible");assertThat(ref.sourceVersion()).isEqualTo(3);assertThat(ref.evidence().actualSeconds()).isNull();});
        assertThat(FullAttendanceBonus.status(captured.sources(),month,cutoff)).isEqualTo("deferred");
    }

    @Test void explicitContractWithoutLeaveDeductionOrSeparateOvertimeNeedsNoAttendanceApproval() {
        var daily=mock(com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService.class);
        var tenant=UUID.randomUUID();var company=UUID.randomUUID();var employee=UUID.randomUUID();var store=UUID.randomUUID();
        var month=java.time.YearMonth.of(2026,10);
        var terms=new ContractPayCalculator.Terms("full","no_deduction","included");
        var rule=new Rule("monthly","2600","8","44",false,"1.5",List.of(),new Generation("contract",List.of(),terms));
        var profile=new PayProfile(UUID.randomUUID(),company,employee,"Contract",month.atDay(1),UUID.randomUUID(),1,rule,null,1);
        var pending=new com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.EffectiveDay(UUID.randomUUID(),store,month.atDay(1),"contract","not_applicable","not_applicable","a".repeat(64),0,null,null,0,0,0,java.util.Map.of("policy","explicit unchanged contract policy"),List.of("PENDING_REQUEST","LEAVE_UNIT_POLICY_REQUIRED"));
        when(daily.capture(eq(tenant),eq(company),eq(employee),any(),any())).thenReturn(List.of(pending));
        var captured=new AttendancePayrollSources(daily).capture(tenant,company,employee,month,profile);
        assertThat(captured.issues()).isEmpty();
        assertThat(captured.sources()).singleElement().satisfies(ref->{assertThat(ref.sourceType()).isEqualTo("contract");assertThat(ref.evidence().actualSeconds()).isNull();assertThat(ref.evidence().approvedSeconds()).isNull();});
    }
    @Test void leaveRecordsDoNotBlockOrChangeTheFreeFixedSalarySource() {
        var daily=mock(com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService.class);
        var tenant=UUID.randomUUID();var company=UUID.randomUUID();var employee=UUID.randomUUID();var store=UUID.randomUUID();
        var month=java.time.YearMonth.of(2026,10);var id=UUID.randomUUID();
        var rule=new Rule("monthly","3000","8","44",false,"1.5",List.of(),new Generation("fixed",List.of()));
        var profile=new PayProfile(UUID.randomUUID(),company,employee,"Free",month.atDay(1),UUID.randomUUID(),1,rule,null,1);
        var day=new com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.EffectiveDay(id,store,month.atDay(1),"free","not_applicable","not_applicable","a".repeat(64),1,null,null,0,0,0,java.util.Map.of("policy","same policy"),List.of());
        when(daily.capture(eq(tenant),eq(company),eq(employee),any(),any())).thenReturn(List.of(day));
        var adapter=new AttendancePayrollSources(daily);var before=adapter.capture(tenant,company,employee,month,profile);
        var pending=new com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.EffectiveDay(id,store,month.atDay(1),"free","not_applicable","not_applicable","b".repeat(64),2,null,null,0,0,0,java.util.Map.of("policy","same policy"),List.of("PENDING_REQUEST"));
        when(daily.capture(eq(tenant),eq(company),eq(employee),any(),any())).thenReturn(List.of(pending));
        var after=adapter.capture(tenant,company,employee,month,profile);
        assertThat(after.issues()).isEmpty();
        assertThat(after.sources()).isEmpty();
        assertThat(after.tokens()).isEqualTo(before.tokens());
    }
    @Test void explicitZeroRestDayDoesNotBecomeAnUnexpectedMonthlyWorkingDay() {
        var daily=mock(com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService.class);
        var tenant=UUID.randomUUID();var company=UUID.randomUUID();var employee=UUID.randomUUID();
        var month=java.time.YearMonth.of(2026,10);
        var rest=new com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.EffectiveDay(UUID.randomUUID(),UUID.randomUUID(),month.atDay(31),"default_normal","rest","arrangement","b".repeat(64),1,null,0L,0,0,0,java.util.Map.of(),List.of());
        when(daily.capture(eq(tenant),eq(company),eq(employee),any(),any())).thenReturn(List.of(rest));
        var rule=new Rule("monthly","2600","8","44",true,"1.5",List.of(),new Generation("attendance",List.of(1,2,3,4,5)));
        var profile=new PayProfile(UUID.randomUUID(),company,employee,"Test",month.atDay(1),UUID.randomUUID(),1,rule,null,1);
        var captured=new AttendancePayrollSources(daily).capture(tenant,company,employee,month,profile);
        assertThat(captured.sources()).isEmpty();
        assertThat(captured.issues()).isEmpty();
        assertThat(captured.tokens()).hasSize(1);
    }
    @Test void arrangementSourceHasItsOwnIdentityAndNeverClaimsAnActualPunch() {
        var id = UUID.randomUUID();
        var evidence = new SourceEvidence(null, "current", 28800L, null, null, List.of(), 3600L, "default_normal");
        var ref = new SourceRef(id, UUID.randomUUID(), LocalDate.of(2026, 10, 1), 3L, "a".repeat(64),
            28800, 0, 0, true, evidence, "arrangement");
        assertThat(ref.sourceId()).isEqualTo(id);
        assertThat(ref.sourceVersion()).isEqualTo(3);
        assertThat(ref.sourceType()).isEqualTo("arrangement");
        assertThat(ref.evidence().actualSeconds()).isNull();
        assertThat(ref.evidence().extraSeconds()).isEqualTo(3600);
    }

    @Test void contractGenerationRequiresExplicitTermsAndDisablesAttendanceOvertime() {
        var terms = new ContractPayCalculator.Terms("working_days", "deduct", "separate");
        var generation = new Generation("contract", List.of(1, 2, 3, 4, 5), terms);
        var rule = new Rule("monthly", "2600", "8", "44", false, "1.5", List.of(), generation);
        assertThat(PayrollGeneration.validate(rule)).isEqualTo(generation);
        assertThat(PayrollGeneration.contract(rule)).isTrue();
        assertThatThrownBy(() -> PayrollGeneration.validate(new Rule("monthly", "2600", "8", "44", true,
            "1.5", List.of(), generation))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(() -> PayrollGeneration.validate(new Rule("monthly", "2600", "8", "44", false,
            "1.5", List.of(), new Generation("contract", List.of(1, 2, 3, 4, 5), null))))
            .isInstanceOf(WorkforceException.class);
    }
}
