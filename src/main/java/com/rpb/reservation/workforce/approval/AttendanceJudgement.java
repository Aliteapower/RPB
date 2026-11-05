package com.rpb.reservation.workforce.approval;
import com.rpb.reservation.workforce.scheduling.rules.AttendanceRules;import com.rpb.reservation.workforce.attendance.*;import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;import java.time.*;import java.util.*;
/** Findings from captured rules and facts. No salary amounts and no automatic absence decision. */
public final class AttendanceJudgement {
 public List<Finding> findings(Schedule s,Session session,List<ApprovalCalculator.LeaveSlice> leaves,Instant now){
  var r=s.shift().rules();if(r==null||!s.status().equals("scheduled")||now.isBefore(s.endAt()))return List.of();var excluded=new ArrayList<Interval>();for(var b:r.breaks())if(b.mode().equals("window")&&b.startOffset()!=null)excluded.add(new Interval(s.startAt().plusSeconds(b.startOffset()*60L),s.startAt().plusSeconds(b.endOffset()*60L)));for(var leave:leaves)excluded.add(new Interval(leave.start(),leave.end()));var working=BreakCalculator.subtract(s.startAt(),s.endAt(),excluded);var result=new ArrayList<Finding>();
  if(session==null){long expected=working.stream().mapToLong(i->BreakCalculator.seconds(i.start(),i.end())).sum();if(expected>0)add(result,r,"MISSING",expected,"pending");}
  else{
   long late=working.stream().mapToLong(i->session.inAt().isAfter(i.start())?BreakCalculator.overlap(i.start(),i.end(),s.startAt(),session.inAt()):0).sum();add(result,r,"LATE",grace(late,r.lateGraceMinutes(),r.graceMode()),"pending");
   if(session.outAt()!=null){long early=working.stream().mapToLong(i->session.outAt().isBefore(i.end())?BreakCalculator.overlap(i.start(),i.end(),session.outAt(),s.endAt()):0).sum();add(result,r,"EARLY",grace(early,r.earlyGraceMinutes(),r.graceMode()),"pending");var summaries=new BreakCalculator().summary(s.shift(),s.startAt(),session);long over=summaries.stream().filter(b->b.actualSeconds()!=null).mapToLong(BreakCalculator.Summary::overSeconds).sum(),shortage=summaries.stream().filter(b->b.actualSeconds()!=null).mapToLong(BreakCalculator.Summary::shortSeconds).sum();add(result,r,"BREAK_OVER",grace(over,r.breakGraceMinutes(),r.graceMode()),"pending");add(result,r,"BREAK_SHORT",grace(shortage,r.breakGraceMinutes(),r.graceMode()),"pending");if(summaries.stream().anyMatch(BreakCalculator.Summary::missing))add(result,r,"BREAK_MISSING",1,"pending");}
  }
  long leaveSeconds=leaves.stream().mapToLong(l->BreakCalculator.seconds(l.start(),l.end())).sum();add(result,r,"LEAVE",leaveSeconds,"confirmed");return List.copyOf(result);
 }
 private long grace(long value,int minutes,String mode){long grace=minutes*60L;return value<=grace?0:mode.equals("excess")?value-grace:value;}
 private void add(List<Finding> rows,AttendanceRules rules,String code,long seconds,String status){var rule=rules.rule(code);if(rule!=null&&seconds>0)rows.add(new Finding(code,rule.name(),seconds,status,rule.affectsBonus()));}
}

