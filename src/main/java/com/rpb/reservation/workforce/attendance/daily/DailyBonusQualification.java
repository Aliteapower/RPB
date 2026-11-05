package com.rpb.reservation.workforce.attendance.daily;

import com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.Interval;
import com.rpb.reservation.workforce.attendance.BreakCalculator;
import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyDtos.IntervalException;
import java.time.Instant;
import java.util.*;

/** Bonus evidence uses captured rules; absence of a punch is never a default-normal finding. */
public final class DailyBonusQualification {
    private DailyBonusQualification() {}
    public static String aggregate(String mode,String state,List<String> issues,List<String> statuses) {
        if(!Set.of("clock","default_normal").contains(mode)||!Set.of("system_valid","human_valid","rest").contains(state)
            ||!issues.isEmpty()||statuses.isEmpty()||statuses.stream().anyMatch(s->!Set.of("eligible","ineligible").contains(s)))return "pending";
        return statuses.contains("ineligible")?"ineligible":"eligible";
    }
    public static String source(String mode,ApprovalSource source,ApprovalRow row,List<IntervalException> exceptions,Instant now) {
        var schedule=source.schedule();var rules=schedule.shift().rules();
        if(rules==null||now.isBefore(schedule.endAt())||!Set.of("clock","default_normal").contains(mode))return "pending";
        if(source.corrections().stream().anyMatch(c->c.status().equals("pending"))
            ||source.leaves().stream().anyMatch(l->Set.of("pending","cancel_requested").contains(l.status()))
            ||exceptions.stream().anyMatch(e->Set.of("pending","cancel_requested").contains(e.status())&&overlaps(e.startAt(),e.endAt(),schedule.startAt(),schedule.endAt())))return "pending";
        var absences=exceptions.stream().filter(e->e.kind().equals("absence")&&e.status().equals("approved")&&overlaps(e.startAt(),e.endAt(),schedule.startAt(),schedule.endAt())).toList();
        if(mode.equals("clock")&&row!=null&&absences.isEmpty()&&!row.requiresReview()&&row.issues().isEmpty()&&Set.of("eligible","ineligible").contains(Objects.toString(row.fullAttendanceStatus(),"")))return row.fullAttendanceStatus();
        boolean ineligible=false;
        if(!absences.isEmpty()) {var rule=rules.rule("ABSENCE");if(rule==null)return "pending";ineligible=rule.affectsBonus();}
        boolean leave=source.leaves().stream().anyMatch(l->l.status().equals("approved")&&overlaps(l.startAt(),l.endAt(),schedule.startAt(),schedule.endAt()));
        if(leave){var rule=rules.rule("LEAVE");if(rule==null)return "pending";ineligible|=rule.affectsBonus();}
        if(mode.equals("default_normal"))return ineligible?"ineligible":"eligible";
        if(row==null)return "pending";
        if(source.session()!=null&&source.session().outAt()==null)return "pending";
        if(source.session()==null&&!covered(source,schedule.startAt(),schedule.endAt(),absences))return "pending";
        for(var finding:row.findings()==null?List.<Finding>of():row.findings()) {
            if(finding.status().equals("pending")) {
                Instant from,to;
                if(finding.code().equals("MISSING")&&source.session()==null){from=schedule.startAt();to=schedule.endAt();}
                else if(finding.code().equals("LATE")&&source.session()!=null){from=schedule.startAt();to=source.session().inAt();}
                else if(finding.code().equals("EARLY")&&source.session()!=null&&source.session().outAt()!=null){from=source.session().outAt();to=schedule.endAt();}
                else return "pending";
                if(absences.isEmpty()||!covered(source,from,to,absences))return "pending";
                var rule=rules.rule("ABSENCE");if(rule==null)return "pending";ineligible|=rule.affectsBonus();
            }else if(!Set.of("excused","confirmed","absence","non_work").contains(finding.status()))return "pending";
            else if(!finding.status().equals("excused"))ineligible|=finding.affectsBonus();
        }
        return ineligible?"ineligible":"eligible";
    }
    private static boolean covered(ApprovalSource source,Instant from,Instant to,List<IntervalException> absences) {
        if(!to.isAfter(from))return true;
        var excluded=new ArrayList<Interval>();
        for(var b:source.schedule().shift().rules().breaks())if(b.mode().equals("window")&&b.startOffset()!=null)
            excluded.add(new Interval(source.schedule().startAt().plusSeconds(b.startOffset()*60L),source.schedule().startAt().plusSeconds(b.endOffset()*60L)));
        for(var l:source.leaves())if(l.status().equals("approved"))excluded.add(new Interval(l.startAt(),l.endAt()));
        for(var e:absences)excluded.add(new Interval(e.startAt(),e.endAt()));
        return BreakCalculator.subtract(from,to,excluded).isEmpty();
    }
    private static boolean overlaps(Instant a,Instant b,Instant from,Instant to){return a.isBefore(to)&&b.isAfter(from);}
}
