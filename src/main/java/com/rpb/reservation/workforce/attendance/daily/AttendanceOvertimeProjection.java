package com.rpb.reservation.workforce.attendance.daily;

import static com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.application.WorkforceAccess;
import com.rpb.reservation.workforce.compliance.ComplianceEvidence;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;

/** Hours only; reads a lawful profile internally and never returns its rate or contract amounts. */
@Service
public class AttendanceOvertimeProjection {
    private final PayrollConfigurationRepository profiles;
    private final WorkforceAccess access;
    private ComplianceEvidence compliance;
    @org.springframework.beans.factory.annotation.Autowired
    public void compliance(ComplianceEvidence compliance) { this.compliance = compliance; }
    public AttendanceOvertimeProjection(PayrollConfigurationRepository profiles, WorkforceAccess access) {
        this.profiles = profiles; this.access = access;
    }

    public static List<EffectiveDay> allocate(PayCalculator.Rule rule, List<EffectiveDay> context, ContractPayCalculator.Terms terms) {
        return allocate(rule, context, terms, null);
    }

    public static List<EffectiveDay> allocate(PayCalculator.Rule rule, List<EffectiveDay> context, ContractPayCalculator.Terms terms, ComplianceEvidence.Terms evidence) {
        var configuration = OvertimeConfiguration.resolve(rule, evidence);
        var ordered = context.stream().sorted(Comparator.comparing(EffectiveDay::workDate)).toList();
        var work = ordered.stream().filter(d -> Set.of("clock", "default_normal").contains(d.mode()))
            .filter(d -> d.workSeconds() != null).map(d -> new PayCalculator.WorkDay(d.workDate(), d.workSeconds(), d.paidLeaveSeconds(), true)).toList();
        var allocations = new HashMap<LocalDate, DailyWorkAllocation>();
        configuration.allocate(work).forEach(d -> allocations.put(d.workDate(), d));
        var unresolvedWeeks = new HashSet<LocalDate>();
        var result = new ArrayList<EffectiveDay>();
        for (var day : ordered) {
            Long overtime = null;
            String state = "unavailable";
            if (day.mode().equals("free") || day.state().equals("outside_employment") || day.state().equals("future")) state = "not_applicable";
            else if (day.mode().equals("contract")) {
                if (terms != null && !terms.overtimeTreatment().equals("separate")) state = "not_applicable";
                else if (terms != null && day.state().equals("not_applicable") && day.issues().isEmpty()) {
                    overtime = day.extraSeconds(); state = "current";
                }
            } else {
                var monday = day.workDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                boolean valid = Set.of("system_valid", "human_valid", "rest").contains(day.state()) && day.issues().stream().allMatch("WORK_ARRANGEMENT_MISSING"::equals);
                if (!valid) unresolvedWeeks.add(monday);
                var allocation = allocations.get(day.workDate());
                if (valid && allocation != null) {
                    overtime = allocation.overtimeSeconds();
                    state = configuration.premium() && unresolvedWeeks.contains(monday) ? "provisional" : "current";
                }
            }
            result.add(withOvertime(day, overtime, state));
        }
        return List.copyOf(result);
    }

    public EmployeeMonth project(CurrentActor actor, UUID store, UUID company, EmployeeMonth row, YearMonth month, List<EffectiveDay> context) {
        var shops = new HashSet<UUID>();
        for (var day : context) {
            if (day.storeId() != null) shops.add(day.storeId());
            if (day.snapshot().get("storeIds") instanceof List<?> ids) for (var id : ids) shops.add(UUID.fromString(id.toString()));
        }
        boolean manager = access.managedStores(actor).contains(store);
        if (shops.stream().anyMatch(s -> !actor.canAccessStore(s) || manager && !access.managedStores(actor).contains(s)))
            return summarize(row, row.days().stream().map(d -> withOvertime(d, null, "scope_restricted")).toList());
        for (var shop : shops) access.enter(actor, shop);
        var proof = profiles.effective().context(actor.tenantId(), company, month);
        var candidates = profiles.profiles(actor.tenantId(), company).stream().filter(p -> p.employeeId().equals(row.employeeId())
            && !p.effectiveFrom().isAfter(month.atEndOfMonth()) && proof.profile(p) != null).sorted(Comparator.comparing(PayrollDtos.PayProfile::effectiveFrom)).toList();
        var firstDay=row.days().stream().filter(d->!d.state().equals("outside_employment")).map(EffectiveDay::workDate).min(LocalDate::compareTo).orElse(month.atDay(1));
        if (candidates.isEmpty() || candidates.getLast().effectiveFrom().isAfter(firstDay))
            return summarize(row, row.days().stream().map(d -> withOvertime(d, null, "unavailable")).toList());
        var profile = candidates.getLast();
        var r = profile.rule();
        boolean mismatch=context.stream().filter(d->!d.workDate().isBefore(month.atDay(1))&&!d.state().equals("outside_employment")).anyMatch(d->d.mode().equals("free")?!PayrollGeneration.fixed(r):d.mode().equals("contract")?!PayrollGeneration.contract(r):PayrollGeneration.fixed(r)||PayrollGeneration.contract(r));
        var rule = calculationRule(r);
        // Resolve adoption by employee/date, never by schedule: default-normal days can have no schedule.
        boolean hourlyArrangement = !PayrollGeneration.fixed(r) && !PayrollGeneration.contract(r);
        var evidence = compliance == null || !hourlyArrangement ? null : compliance.on(actor.tenantId(), row.employeeId(), month.atEndOfMonth());
        var configuration = OvertimeConfiguration.resolve(rule, evidence);
        boolean configurationMismatch = false;
        if (hourlyArrangement) {
            for (var day : context) {
                if (!Set.of("clock", "default_normal").contains(day.mode()) || Set.of("outside_employment", "future").contains(day.state())) continue;
                var before = candidates.stream().filter(p -> !p.effectiveFrom().isAfter(day.workDate())).max(Comparator.comparing(PayrollDtos.PayProfile::effectiveFrom)).orElse(null);
                var on = compliance == null ? null : compliance.on(actor.tenantId(), row.employeeId(), day.workDate());
                if (before == null || !validEvidence(on, company, day.workDate()) || (evidence == null) != (on == null)
                    || !configuration.sameThresholds(OvertimeConfiguration.resolve(calculationRule(before.rule()), on))) {
                    configurationMismatch = true; break;
                }
            }
            configurationMismatch |= !validEvidence(evidence, company, month.atEndOfMonth());
        }
        if(mismatch||configurationMismatch)return summarize(row,row.days().stream().map(d->withOvertime(d,null,"unavailable")).toList());
        var projected = new HashMap<LocalDate, EffectiveDay>();
        allocate(rule, context, PayrollGeneration.contract(r) ? r.generation().contractTerms() : null,
            PayrollGeneration.fixed(r) || PayrollGeneration.contract(r) ? null : evidence).forEach(d -> projected.put(d.workDate(), d));
        var days = row.days().stream().map(d -> {
            var p = projected.get(d.workDate());
            return p == null || d.issues().contains("SCOPE_RESTRICTED") ? withOvertime(d, null, "scope_restricted") : withOvertime(d, p.overtimeSeconds(), p.overtimeState());
        }).toList();
        return summarize(row, days);
    }

    private static PayCalculator.Rule calculationRule(PayrollDtos.Rule r) {
        return new PayCalculator.Rule(r.basis(), new BigDecimal(r.rate()), new BigDecimal(r.dayHours()), new BigDecimal(r.weekHours()), r.overtimeEnabled(), new BigDecimal(r.overtimeMultiplier()));
    }

    private static boolean validEvidence(ComplianceEvidence.Terms terms, UUID company, LocalDate date) {
        return terms == null || terms.contract().companyId().equals(company)
            && !terms.contract().effectiveFrom().isAfter(date) && !terms.policy().effectiveFrom().isAfter(date)
            && !terms.policy().effectiveTo().isBefore(date) && "published".equals(terms.policy().status());
    }

    private static EffectiveDay withOvertime(EffectiveDay d, Long seconds, String state) {
        return new EffectiveDay(d.sourceId(), d.storeId(), d.workDate(), d.mode(), d.state(), d.sourceType(), d.sourceFingerprint(), d.version(), d.actualSeconds(), d.workSeconds(), d.paidLeaveSeconds(), d.unpaidLeaveSeconds(), d.extraSeconds(), d.snapshot(), d.issues(), seconds, state);
    }
    private static EmployeeMonth summarize(EmployeeMonth row, List<EffectiveDay> days) {
        boolean hasCurrent = days.stream().anyMatch(d -> d.overtimeState().equals("current"));
        Long approved = hasCurrent ? days.stream().filter(d -> d.overtimeState().equals("current") && d.overtimeSeconds() != null).mapToLong(EffectiveDay::overtimeSeconds).sum() : null;
        Long pending = days.stream().anyMatch(d -> Set.of("unavailable", "scope_restricted").contains(d.overtimeState())) ? null
            : days.stream().filter(d -> d.overtimeState().equals("provisional") && d.overtimeSeconds() != null).mapToLong(EffectiveDay::overtimeSeconds).sum();
        var t = row.totals();
        return new EmployeeMonth(row.employeeId(), row.employeeNo(), row.name(), days, new MonthTotals(t.workSeconds(), t.paidLeaveSeconds(), t.unpaidLeaveSeconds(), t.absenceSeconds(), t.extraWorkSeconds(), t.pendingCount(), approved, pending));
    }
}
