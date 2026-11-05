package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.attendance.daily.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.compliance.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AttendanceOvertimeProjectionTest {
    private final UUID tenant = UUID.randomUUID(), company = UUID.randomUUID(), employee = UUID.randomUUID();
    private class ProjectionFixture {
        final PayrollConfigurationRepository profiles = mock(PayrollConfigurationRepository.class, RETURNS_DEEP_STUBS);
        final com.rpb.reservation.workforce.application.WorkforceAccess access = mock(com.rpb.reservation.workforce.application.WorkforceAccess.class);
        final ComplianceEvidence compliance = mock(ComplianceEvidence.class);
        final AttendanceOvertimeProjection projection = new AttendanceOvertimeProjection(profiles,access);
        final AttendanceDailyDtos.EffectiveDay source = day(3,"default_normal","system_valid",32400L,0);
        final com.rpb.reservation.walkin.api.CurrentActor actor = new com.rpb.reservation.walkin.api.CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(source.storeId()));
        final AttendanceDailyDtos.EmployeeMonth row = new AttendanceDailyDtos.EmployeeMonth(employee,"001","Synthetic",List.of(source),new AttendanceDailyDtos.MonthTotals(32400L,0,0,0,0,0));
        final YearMonth month = YearMonth.of(2026,10);
        ProjectionFixture() {
            projection.compliance(compliance);
            var rule = new PayrollDtos.Rule("hourly","10","4","20",false,"1",List.of());
            var profile = new PayrollDtos.PayProfile(UUID.randomUUID(),company,employee,"Synthetic",LocalDate.of(2026,1,1),UUID.randomUUID(),1,rule,null,1);
            when(profiles.profiles(tenant,company)).thenReturn(List.of(profile));
            when(profiles.effective().context(tenant,company,month).profile(profile)).thenReturn(new com.rpb.reservation.workforce.payroll.governance.EffectivePayGovernance.Evidence("approved",profile.id(),1,null,null,null));
        }
        AttendanceDailyDtos.EmployeeMonth project(List<AttendanceDailyDtos.EffectiveDay> context) {
            return projection.project(actor,source.storeId(),company,row,month,context);
        }
        AttendanceDailyDtos.EffectiveDay prior() {
            return new AttendanceDailyDtos.EffectiveDay(UUID.randomUUID(),source.storeId(),LocalDate.of(2026,9,30),"default_normal","system_valid","arrangement","a".repeat(64),1,null,28800L,0,0,0,Map.of(),List.of());
        }
        ComplianceEvidence.Terms adopt(ComplianceEvidence.Terms terms) {
            var c=terms.contract();
            var matching=new ComplianceEvidence.Terms(new ComplianceDtos.Contract(c.id(),employee,company,c.version(),c.effectiveFrom(),c.serviceStart(),c.policyId(),c.policyVersion(),c.dailyHours(),c.weeklyHours(),c.weeklyDays(),c.comparableDailyHours(),c.comparableWeeklyHours(),c.category(),c.evidence(),c.partTime()),terms.policy());
            when(compliance.on(eq(tenant),eq(employee),any(LocalDate.class))).thenReturn(matching);
            return matching;
        }
    }
    @Test void projectResolvesAdoptedTermsWithoutScheduleAndKeepsSensitiveConfigurationInternal() {
        var f = new ProjectionFixture();f.adopt(adopted(true));
        var result=f.project(List.of(f.source));
        assertThat(result.days().getFirst().overtimeSeconds()).isEqualTo(3600);
        assertThat(result.days().getFirst().actualSeconds()).isNull();
        assertThat(result.days().getFirst().snapshot()).isEmpty();
        assertThat(result.totals().overtimeSeconds()).isEqualTo(3600);
        verify(f.compliance).on(tenant,employee,f.source.workDate());
    }
    @Test void missingProfileOrCrossMonthAdoptionChangeIsUnavailable() {
        var f = new ProjectionFixture();f.adopt(adopted(true));
        var prior=f.prior();
        when(f.compliance.on(tenant,employee,prior.workDate())).thenReturn(null);
        var result=f.project(List.of(prior,f.source));
        assertThat(result.days().getFirst().overtimeSeconds()).isNull();
        assertThat(result.days().getFirst().overtimeState()).isEqualTo("unavailable");
        when(f.profiles.profiles(tenant,company)).thenReturn(List.of());
        assertThat(f.project(List.of(f.source)).totals().overtimeSeconds()).isNull();
    }
    @Test void differentStoreScopeDoesNotReadSensitiveConfiguration() {
        var f=new ProjectionFixture();
        var hidden=day(2,"clock","human_valid",28800L,0);
        assertThat(f.project(List.of(hidden,f.source)).days().getFirst().overtimeState()).isEqualTo("scope_restricted");
        verifyNoInteractions(f.compliance);
        verify(f.profiles,never()).profiles(any(),any());
    }
    @Test void expiredAdoptedPolicyAndChangedCrossMonthThresholdsAreUnavailable() {
        var f=new ProjectionFixture();var terms=f.adopt(adopted(false));
        var c=terms.contract();var prior=f.prior();
        var changed=new ComplianceEvidence.Terms(new ComplianceDtos.Contract(c.id(),employee,company,c.version(),c.effectiveFrom(),c.serviceStart(),c.policyId(),c.policyVersion(),new BigDecimal("7"),c.weeklyHours(),c.weeklyDays(),c.comparableDailyHours(),c.comparableWeeklyHours(),c.category(),c.evidence(),false),terms.policy());
        when(f.compliance.on(tenant,employee,prior.workDate())).thenReturn(changed);
        assertThat(f.project(List.of(prior,f.source)).days().getFirst().overtimeState()).isEqualTo("unavailable");
        var p=terms.policy();
        f.adopt(new ComplianceEvidence.Terms(c,new ComplianceDtos.Policy(p.id(),p.version(),p.name(),p.effectiveFrom(),LocalDate.of(2026,9,30),p.status(),p.rules(),p.sources(),p.reviewerId())));
        assertThat(f.project(List.of(f.source)).days().getFirst().overtimeState()).isEqualTo("unavailable");
    }
    private ComplianceEvidence.Terms adopted(boolean partTime) {
        var ref = ComplianceReference.singapore();
        var id = UUID.randomUUID();
        return new ComplianceEvidence.Terms(new ComplianceDtos.Contract(id,id,id,1,LocalDate.of(2026,1,1),LocalDate.of(2026,1,1),id,1,
            new BigDecimal(partTime ? "4" : "8"),new BigDecimal(partTime ? "20" : "44"),new BigDecimal("5"),
            new BigDecimal("8"),new BigDecimal("44"),"non_workman","Reviewed",partTime),
            new ComplianceDtos.Policy(id,1,ref.name(),LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),"published",ref.rules(),ref.sources(),id));
    }
    @Test void adoptedTermsUseSameDailyAndPartTimeThresholdsAsPayrollWithoutSchedule() {
        for (boolean partTime : List.of(false,true)) {
            var terms = adopted(partTime);
            var disabled = new PayCalculator.Rule("hourly",BigDecimal.TEN,new BigDecimal("4"),new BigDecimal("20"),false,BigDecimal.ONE);
            for (var mode : List.of("clock","default_normal")) {
                var projected = AttendanceOvertimeProjection.allocate(disabled,List.of(day(0,mode,"system_valid",32400L,0)),null,terms);
                var payroll = new PayCalculator().calculate(disabled,List.of(new PayCalculator.WorkDay(day(0,mode,"system_valid",32400L,0).workDate(),32400,0,true)),new PayCalculator.Units(null,null),List.of(),List.of(),terms);
                assertThat(projected.getFirst().overtimeSeconds()).isEqualTo(3600).isEqualTo(payroll.overtimeSeconds());
                assertThat(projected.getFirst().overtimeState()).isEqualTo("current");
                assertThat(projected.getFirst().actualSeconds()).isNull();
            }
        }
    }
    @Test void adoptedWeeklyLimitAndUnresolvedPrefixMatchPayroll() {
        var disabled = new PayCalculator.Rule("hourly",BigDecimal.TEN,new BigDecimal("4"),new BigDecimal("20"),false,BigDecimal.ONE);
        var context = new ArrayList<AttendanceDailyDtos.EffectiveDay>();
        for(int i=0;i<6;i++)context.add(day(i,"default_normal","system_valid",28800L,0));
        var result = AttendanceOvertimeProjection.allocate(disabled,context,null,adopted(true));
        assertThat(result.getLast().overtimeSeconds()).isEqualTo(14400);
        context.set(0,day(0,"clock","pending",null,0));
        assertThat(AttendanceOvertimeProjection.allocate(disabled,context,null,adopted(true)).getLast().overtimeState()).isEqualTo("provisional");
    }
    @Test void adoptedTermsDoNotOverrideContractOrFreeModes() {
        var result = AttendanceOvertimeProjection.allocate(rule,List.of(day(0,"contract","not_applicable",null,5400),day(1,"free","not_applicable",null,0)),new ContractPayCalculator.Terms("full","no_deduction","included"),adopted(true));
        assertThat(result).allSatisfy(d->{assertThat(d.overtimeSeconds()).isNull();assertThat(d.overtimeState()).isEqualTo("not_applicable");});
    }
    private final PayCalculator.Rule rule = new PayCalculator.Rule("hourly", BigDecimal.TEN,
        new BigDecimal("8"), new BigDecimal("44"), true, new BigDecimal("1.5"));
    private AttendanceDailyDtos.EffectiveDay day(int offset, String mode, String state, Long work, long extra) {
        return new AttendanceDailyDtos.EffectiveDay(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 28).plusDays(offset),
            mode, state, "arrangement", "a".repeat(64), 1, null, work, 0, 0, extra, Map.of(), List.of());
    }
    @Test void dailyCalendarOvertimeMatchesPayrollAndKeepsArrangementActualUnknown() {
        var days = AttendanceOvertimeProjection.allocate(rule, List.of(day(0, "default_normal", "system_valid", 36000L, 0)), null);
        assertThat(days.getFirst().overtimeSeconds()).isEqualTo(7200);
        assertThat(days.getFirst().overtimeState()).isEqualTo("current");
        assertThat(days.getFirst().actualSeconds()).isNull();
    }
    @Test void unresolvedEarlierWeekSourceMakesLaterAllocationProvisional() {
        var days = AttendanceOvertimeProjection.allocate(rule, List.of(day(0, "clock", "pending", null, 0),
            day(1, "clock", "human_valid", 36000L, 0)), null);
        assertThat(days.get(0).overtimeSeconds()).isNull();
        assertThat(days.get(1).overtimeSeconds()).isEqualTo(7200);
        assertThat(days.get(1).overtimeState()).isEqualTo("provisional");
    }
    @Test void contractOvertimeUsesOnlyDeclaredApprovedExtraUnits() {
        var days = AttendanceOvertimeProjection.allocate(rule, List.of(day(0, "contract", "not_applicable", null, 5400)),
            new ContractPayCalculator.Terms("full", "no_deduction", "separate"));
        assertThat(days.getFirst().overtimeSeconds()).isEqualTo(5400);
        var included = AttendanceOvertimeProjection.allocate(rule, List.of(day(0, "contract", "not_applicable", null, 5400)),
            new ContractPayCalculator.Terms("full", "no_deduction", "included"));
        assertThat(included.getFirst().overtimeSeconds()).isNull();
        assertThat(included.getFirst().overtimeState()).isEqualTo("not_applicable");
    }
}
