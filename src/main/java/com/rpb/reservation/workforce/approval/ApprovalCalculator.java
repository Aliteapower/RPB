package com.rpb.reservation.workforce.approval;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import com.rpb.reservation.workforce.attendance.AttendanceCalculator;
import com.rpb.reservation.workforce.attendance.BreakCalculator;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Schedule;
import java.time.*;import java.util.*;
/** Facts only: no statutory overtime or monetary calculations. */
public final class ApprovalCalculator {
    public record LeaveSlice(Instant start,Instant end,boolean paid){}
    public record Totals(long workSeconds,long paidLeaveSeconds,long unpaidLeaveSeconds,List<String> issues){}
    public Totals calculate(Schedule s,Session session,List<LeaveSlice> leaves,Instant now,boolean pending){
        var issues=new LinkedHashSet<String>();
        if(!s.status().equals("scheduled"))issues.add("CANCELLED");
        if(now.isBefore(s.endAt()))issues.add("SHIFT_NOT_ENDED");
        if(pending)issues.add("PENDING_REQUEST");
        long grossMicros=micros(s.startAt(),s.endAt()),rawMicros=0,paidRawMicros=0,netMicros=0,paidNetMicros=0;
        boolean continuous=true;
        var sorted=new ArrayList<>(leaves);sorted.sort(Comparator.comparing(LeaveSlice::start));Instant previous=s.startAt();
        for(var l:sorted){
            if(l.start().isBefore(s.startAt())||l.end().isAfter(s.endAt())||!l.end().isAfter(l.start())||l.start().isBefore(previous)){issues.add("LEAVE_OVERLAP");continuous=false;continue;}
            if(!l.start().equals(previous))continuous=false;
            previous=l.end();long amount=micros(l.start(),l.end());rawMicros+=amount;if(l.paid())paidRawMicros+=amount;
            if(s.shift().rules()!=null){
                var windows=new ArrayList<Interval>();for(var b:s.shift().rules().breaks())if(!b.paid()&&b.mode().equals("window"))windows.add(new Interval(s.startAt().plusSeconds(b.startOffset()*60L),s.startAt().plusSeconds(b.endOffset()*60L)));amount=BreakCalculator.subtract(l.start(),l.end(),windows).stream().mapToLong(i->micros(i.start(),i.end())).sum();
            }else if(!s.shift().breakPaid()&&s.shift().breakMode().equals("window")){
                Instant a=max(l.start(),s.startAt().plusSeconds(s.shift().breakStartOffset()*60L)),b=min(l.end(),s.startAt().plusSeconds(s.shift().breakEndOffset()*60L));
                if(b.isAfter(a))amount-=micros(a,b);
            }
            netMicros+=amount;if(l.paid())paidNetMicros+=amount;
            if(session!=null&&session.inAt().isBefore(l.end())&&(session.outAt()==null||session.outAt().isAfter(l.start())))issues.add("LEAVE_ATTENDANCE_OVERLAP");
        }
        long leaveNet=netMicros/1_000_000,paidNet=paidNetMicros/1_000_000;
        if(s.shift().rules()==null&&!s.shift().breakPaid()&&s.shift().breakMode().equals("duration")){
            long planned=new AttendanceCalculator().calculate(s.shift(),s.startAt(),s.startAt(),s.endAt(),List.of(),false).workSeconds();
            leaveNet=rawMicros*planned/grossMicros;paidNet=paidRawMicros*planned/grossMicros;
        }
        long work=0;
        if(session==null){if(!continuous||!previous.equals(s.endAt()))issues.add("MISSING_IN");}
        else{
            var result=new AttendanceCalculator().calculate(s.shift(),s.startAt(),session.inAt(),session.outAt(),session.breaks(),session.overrideBreak());
            issues.addAll(result.anomalies());if(session.breakStartedAt()!=null)issues.add("OPEN_BREAK");if(result.workSeconds()!=null)work=result.workSeconds();
        }
        return new Totals(work,paidNet,leaveNet-paidNet,List.copyOf(issues));
    }
    private Instant max(Instant a,Instant b){return a.isAfter(b)?a:b;}
    private Instant min(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private long micros(Instant a,Instant b){return Duration.between(a,b).toNanos()/1000;}
}

