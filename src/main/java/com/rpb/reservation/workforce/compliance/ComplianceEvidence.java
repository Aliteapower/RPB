package com.rpb.reservation.workforce.compliance;

import static com.rpb.reservation.workforce.compliance.ComplianceDtos.*;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.Session;
import com.rpb.reservation.workforce.approval.ApprovalDtos.Finding;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Immutable adoption evidence travels in the existing approval and payroll fingerprints. */
@Component
public class ComplianceEvidence {
    public record Terms(Contract contract, Policy policy) {}
    private final ComplianceRepository repo;
    public ComplianceEvidence(ComplianceRepository repo) {this.repo=repo;}
    public Terms on(UUID tenant,UUID employee,LocalDate date) {
        return repo.contract(tenant,employee,date).map(c->new Terms(c,repo.policy(tenant,c.policyId(),c.policyVersion()).orElseThrow())).orElse(null);
    }
    public List<Finding> findings(Terms terms,Schedule s,Session session,long work) {
        if(terms==null||session==null||session.outAt()==null)return List.of();
        var result=new ArrayList<Finding>();
        long outside=Math.max(0,Duration.between(session.inAt(),s.startAt()).getSeconds())+Math.max(0,Duration.between(s.endAt(),session.outAt()).getSeconds());
        outside=Math.min(work,outside);
        if(outside>0)result.add(new Finding("OUTSIDE_SCHEDULE","超出排班：核实实际工作或非工作逗留",outside,"pending",false));
        if(work>terms.policy().rules().maxDailyHours()*3600L)result.add(new Finding("DAILY_HOURS_LIMIT","超过每日工时上限：须核对安排及处理依据",work,"pending",false));
        var intervals=session.breaks().stream().filter(b->b.end()!=null).sorted(Comparator.comparing(b->b.start())).toList();
        Instant prior=session.inAt();long longest=0;
        for(var b:intervals) {if(b.start().isAfter(prior))longest=Math.max(longest,Duration.between(prior,b.start()).getSeconds());if(b.end().isAfter(prior))prior=b.end();}
        if(session.outAt().isAfter(prior))longest=Math.max(longest,Duration.between(prior,session.outAt()).getSeconds());
        // Recorded breaks are required to substantiate continuous-work compliance; planned breaks are not guessed.
        if(longest>terms.policy().rules().maxContinuousHours()*3600L)result.add(new Finding("CONTINUOUS_HOURS","连续工作时间需核实实际休息及适用安排",longest,"pending",false));
        return List.copyOf(result);
    }
}
