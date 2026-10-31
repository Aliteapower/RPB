package com.rpb.reservation.workforce.attendancepolicy;
import static com.rpb.reservation.workforce.attendancepolicy.AttendancePolicyDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;import java.time.*;import java.util.*;
public final class AttendancePolicyRules {
 private AttendancePolicyRules(){}
 public static String mode(String mode){if(mode==null||!Set.of("clock","default_normal","contract","free").contains(mode))throw WorkforceException.invalid();return mode;}
 public static Arrangement arrangement(String mode,Arrangement a){mode(mode);if(a==null){if(mode.equals("clock")||mode.equals("default_normal"))throw WorkforceException.invalid();return null;}if(a.weekdays()==null||a.weekdays().isEmpty()||a.weekdays().size()>7||new HashSet<>(a.weekdays()).size()!=a.weekdays().size()||a.weekdays().stream().anyMatch(d->d==null||d<1||d>7)||a.startTime()==null||a.startTime().getSecond()!=0||a.startTime().getNano()!=0||a.standardWorkMinutes()<=0||a.standardWorkMinutes()>1440||a.unpaidBreakMinutes()<0||a.unpaidBreakMinutes()>1440-a.standardWorkMinutes()||(a.unpaidBreakMinutes()>0&&(a.unpaidBreakStartOffsetMinutes()==null||a.unpaidBreakStartOffsetMinutes()<0||a.unpaidBreakStartOffsetMinutes()>a.standardWorkMinutes()))||(a.unpaidBreakMinutes()==0&&a.unpaidBreakStartOffsetMinutes()!=null))throw WorkforceException.invalid();return a;}
 public static void date(LocalDate from,LocalDate today,LocalDate hire,LocalDate last){if(from==null||!from.isAfter(today)||from.isBefore(hire)||(last!=null&&!from.isAfter(last)))throw WorkforceException.invalid();}
}
