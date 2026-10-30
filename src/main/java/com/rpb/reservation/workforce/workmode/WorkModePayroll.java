package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import java.time.*;import java.util.*;
/** Payroll consistency only: changing attendance policy never silently changes a contract salary. */
public final class WorkModePayroll {
 private WorkModePayroll(){}
 public static List<String> issues(List<Assignment> history,LocalDate from,LocalDate to,boolean fixed){
  if(from==null||to==null||from.isAfter(to))return List.of();var dates=new TreeSet<LocalDate>();dates.add(from);history.stream().filter(a->!a.effectiveFrom().isBefore(from)&&!a.effectiveFrom().isAfter(to)).forEach(a->dates.add(a.effectiveFrom()));var issues=new LinkedHashSet<String>();var freedom=new HashSet<Boolean>();
  for(var date:dates){String mode=history.stream().filter(a->!a.effectiveFrom().isAfter(date)).max(Comparator.comparing(Assignment::effectiveFrom)).map(Assignment::mode).orElse("legacy");freedom.add(mode.equals("free"));if(!mode.equals("legacy")&&(mode.equals("free")!=fixed))issues.add("WORK_MODE_PAYROLL_MISMATCH");}
  if(freedom.size()>1)issues.add("WORK_MODE_MONTH_SPLIT");return List.copyOf(issues);
 }
}
