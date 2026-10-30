package com.rpb.reservation.workforce.attendance;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Shift;
import com.rpb.reservation.workforce.domain.WorkforceException;
import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;
import java.time.*;
import java.util.*;
/** Pure interval arithmetic. No rounding, payroll or statutory overtime decisions. */
public final class AttendanceCalculator {
    public Calculation calculate(Shift shift,Instant plannedStart,Instant in,Instant out,List<Interval> actual,boolean override){
        if(out==null)return new Calculation(null,null,null,List.of("OPEN"));
        if(in==null||!out.isAfter(in))throw WorkforceException.invalid();
        long gross=Duration.between(in,out).getSeconds();
        if(gross>36*3600)return new Calculation(gross,null,null,List.of("LONG_SHIFT"));
        if(shift.rules()!=null){var result=new BreakCalculator().calculate(shift.rules(),plannedStart,in,out,actual);return new Calculation(gross,result.breakSeconds(),result.workSeconds(),result.issues());}
        long rest=0;
        if(override||"manual".equals(shift.breakMode())){
            if(actual==null||actual.stream().anyMatch(b->b==null||b.start()==null||b.end()==null))throw WorkforceException.invalid();
            var intervals=new ArrayList<>(actual);intervals.sort(Comparator.comparing(Interval::start));Instant previous=in;
            for(var b:intervals){if(b==null||b.start()==null||b.end()==null||b.start().isBefore(previous)||b.end().isAfter(out)||!b.end().isAfter(b.start()))throw WorkforceException.invalid();rest+=Duration.between(b.start(),b.end()).getSeconds();previous=b.end();}
        } else if("duration".equals(shift.breakMode())&&gross>=shift.breakAfterMinutes()*60L)rest=Math.min(gross,shift.breakMinutes()*60L);
        else if("window".equals(shift.breakMode())){
            Instant from=plannedStart.plusSeconds(shift.breakStartOffset()*60L),to=plannedStart.plusSeconds(shift.breakEndOffset()*60L);
            Instant a=in.isAfter(from)?in:from,b=out.isBefore(to)?out:to;
            if(b.isAfter(a))rest=Duration.between(a,b).getSeconds();
        }
        return new Calculation(gross,rest,shift.breakPaid()?gross:gross-rest,List.of());
    }
}
