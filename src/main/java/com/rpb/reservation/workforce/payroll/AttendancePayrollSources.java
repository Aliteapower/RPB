package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;

/** The payroll adapter consumes the same dated, effective daily facts as the calendar. */
@Service
public class AttendancePayrollSources {
    public record Capture(List<SourceRef> sources, List<String> issues, List<Object> tokens, boolean specialDays, Set<UUID> stores, Map<LocalDate,List<String>> datedIssues, List<String> globalIssues, Set<LocalDate> specialDates) {}
    private final AttendanceDailyService daily;
    public AttendancePayrollSources(AttendanceDailyService daily) { this.daily = daily; }

    public Capture capture(UUID tenant, UUID company, UUID employee, YearMonth month, PayProfile profile) {
        return capture(tenant,company,employee,month,profile,month.atEndOfMonth());
    }

    public Capture capture(UUID tenant, UUID company, UUID employee, YearMonth month, PayProfile profile, LocalDate cutoff) {
        return capture(tenant,company,employee,month,profile,cutoff,profile!=null&&profile.rule().overtimeEnabled());
    }

    public Capture capture(UUID tenant, UUID company, UUID employee, YearMonth month, PayProfile profile, LocalDate cutoff, boolean premium) {
        if(cutoff==null||!YearMonth.from(cutoff).equals(month))throw com.rpb.reservation.workforce.domain.WorkforceException.invalid();
        var from = month.atDay(1);
        var context = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var refs = new ArrayList<SourceRef>();
        var issues = new LinkedHashSet<String>();
        var datedIssues=new LinkedHashMap<LocalDate,List<String>>();
        var tokens = new ArrayList<Object>();
        var modes = new HashSet<String>();
        var stores = new HashSet<UUID>();
        boolean special = false;
        var specialDates=new TreeSet<LocalDate>();
        for (var day : daily.capture(tenant, company, employee, context, cutoff)) {
            var dayIssues=new LinkedHashSet<String>();
            try {
            if (day.state().equals("outside_employment")) continue;
            if(day.storeId()!=null)stores.add(day.storeId());
            if(day.snapshot().get("storeIds") instanceof List<?> ids)for(var id:ids)stores.add(UUID.fromString(id.toString()));
            boolean payable = !day.workDate().isBefore(from);
            if (!payable && !premium) continue;
            if (payable) modes.add(day.mode());
            if (day.mode().equals("free")) {
                tokens.add(List.of(employee,day.workDate(),"free",day.snapshot().getOrDefault("policy","current_work_mode_fact")));
                if (payable && profile != null && !PayrollGeneration.fixed(profile.rule())) dayIssues.add("WORK_MODE_PAYROLL_MISMATCH");
                continue;
            }
            tokens.add(List.of(employee, day.workDate(), day.mode(), day.sourceFingerprint(), day.version(), day.state(), day.issues()));
            if(day.state().equals("rest") && day.issues().isEmpty() && Long.valueOf(0).equals(day.workSeconds()) && day.paidLeaveSeconds()==0 && day.unpaidLeaveSeconds()==0 && day.extraSeconds()==0) continue;
            boolean contract = day.mode().equals("contract");
            if (payable && profile != null && (contract != PayrollGeneration.contract(profile.rule()) || PayrollGeneration.fixed(profile.rule())))
                dayIssues.add("WORK_MODE_PAYROLL_MISMATCH");
            if(contract&&profile!=null&&PayrollGeneration.contract(profile.rule())
                &&profile.rule().generation().contractTerms().unpaidLeaveTreatment().equals("no_deduction")
                &&!profile.rule().generation().contractTerms().overtimeTreatment().equals("separate")){
                // This contract's pay units depend on employment/policy, never on informational leave or punches.
                tokens.removeLast();
                var policy=day.snapshot().getOrDefault("policy","explicit_contract");
                tokens.add(List.of(employee,day.workDate(),"contract",policy));
                long version=policy instanceof com.rpb.reservation.workforce.attendancepolicy.AttendancePolicyDtos.EffectiveAttendancePolicy p?p.version():0;
                String fingerprint;
                try{fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((day.workDate()+":"+policy).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
                catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
                var evidence=new SourceEvidence(null,"current",null,null,null,List.of(),0,"contract");
                refs.add(new SourceRef(day.sourceId(),day.storeId(),day.workDate(),version,fingerprint,0,0,0,payable,evidence,"contract"));
                continue;
            }
            if (day.state().equals("unarranged") && !contract) {
                if (premium) dayIssues.add("WEEK_CONTEXT_INCOMPLETE");
                if (!day.issues().isEmpty() && !day.issues().equals(List.of("WORK_ARRANGEMENT_MISSING"))) dayIssues.addAll(day.issues());
                continue;
            }
            boolean valid = contract?day.state().equals("not_applicable")&&day.issues().isEmpty():com.rpb.reservation.workforce.attendance.daily.DailySourceState.payrollReady(day);
            if (!valid) {
                dayIssues.add(day.state().equals("stale") ? "APPROVAL_STALE" : "APPROVAL_MISSING");
                dayIssues.addAll(day.issues());
            }
            if (payable && Boolean.TRUE.equals(day.snapshot().get("specialDayWorked"))) {special = true;specialDates.add(day.workDate());}
            var evidence = new SourceEvidence(day.actualSeconds(), valid ? "current" : day.state().equals("stale") ? "stale" : "missing",
                valid && !contract ? day.workSeconds() : null, null, null, day.snapshot().get("eventIds") instanceof List<?> ids ? ids.stream().map(Object::toString).toList() : List.of(), valid ? day.extraSeconds() : 0, day.mode(), Objects.toString(day.snapshot().get("fullAttendanceStatus"),null));
            refs.add(new SourceRef(day.sourceId(), day.storeId(), day.workDate(), day.version(), day.sourceFingerprint(),
                valid && day.workSeconds() != null ? day.workSeconds() : 0,
                valid ? day.paidLeaveSeconds() : 0, valid ? day.unpaidLeaveSeconds() : 0, payable, evidence,
                contract ? "contract" : day.sourceType()));
            } finally {if(!dayIssues.isEmpty()){datedIssues.put(day.workDate(),List.copyOf(dayIssues));issues.addAll(dayIssues);}}
        }
        if (modes.size() > 1) issues.add("WORK_MODE_MONTH_SPLIT");
        return new Capture(List.copyOf(refs), List.copyOf(issues), List.copyOf(tokens), special, Set.copyOf(stores), Collections.unmodifiableMap(datedIssues), modes.size()>1?List.of("WORK_MODE_MONTH_SPLIT"):List.of(),Set.copyOf(specialDates));
    }
}
