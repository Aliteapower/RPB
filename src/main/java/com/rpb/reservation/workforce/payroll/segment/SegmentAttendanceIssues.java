package com.rpb.reservation.workforce.payroll.segment;

import com.rpb.reservation.workforce.payroll.PayrollSources.AttendanceIssueScope;
import com.rpb.reservation.workforce.payroll.PayrollDtos.SourceRef;
import java.time.LocalDate;
import java.util.*;

/** Only current facts after the frozen cutoff may introduce attendance blockers. */
public final class SegmentAttendanceIssues {
    private SegmentAttendanceIssues() {}
    public static List<String> after(AttendanceIssueScope scope,LocalDate frozenThrough,List<SourceRef> effectiveSources) {
        var issues=new LinkedHashSet<>(scope.independent());
        scope.dated().forEach((date,values)->{if(date.isAfter(frozenThrough))issues.addAll(values);});
        issues.addAll(scope.global());
        if(scope.global().contains("WORK_MODE_MONTH_SPLIT")&&effectiveSources.stream().allMatch(r->r.evidence()!=null&&r.evidence().attendanceMode()!=null)){
            issues.remove("WORK_MODE_MONTH_SPLIT");
            if(effectiveSources.stream().filter(SourceRef::payable).map(r->r.evidence().attendanceMode()).distinct().count()>1)issues.add("WORK_MODE_MONTH_SPLIT");
        }
        return List.copyOf(issues);
    }
}
