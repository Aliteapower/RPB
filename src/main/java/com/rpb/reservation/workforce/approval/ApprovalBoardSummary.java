package com.rpb.reservation.workforce.approval;
import static com.rpb.reservation.workforce.approval.ApprovalBoardDtos.*;
import java.time.Duration;
import java.util.*;
/** Whole filtered date range, before page slicing; unknown evidence is never zero hours. */
public final class ApprovalBoardSummary {
 private ApprovalBoardSummary(){}
 public static List<EmployeeProgress> employees(List<Entry> entries){
  var groups=new LinkedHashMap<UUID,List<Entry>>();entries.forEach(e->groups.computeIfAbsent(e.row().schedule().employeeId(),k->new ArrayList<>()).add(e));
  var result=new ArrayList<EmployeeProgress>();
  for(var rows:groups.values()){
   var s=rows.getFirst().row().schedule();boolean actual=rows.stream().allMatch(e->e.inAt()!=null&&e.outAt()!=null&&!Boolean.TRUE.equals(e.row().exempt()));boolean approved=rows.stream().allMatch(e->e.category().equals("current"));
   result.add(new EmployeeProgress(s.employeeId(),s.employeeName(),s.employeeNo(),rows.size(),(int)rows.stream().filter(e->!Set.of("current","exempt").contains(e.category())).count(),rows.stream().mapToLong(e->Duration.between(e.row().schedule().startAt(),e.row().schedule().endAt()).getSeconds()).sum(),actual?rows.stream().mapToLong(e->e.row().workSeconds()).sum():null,approved?rows.stream().mapToLong(e->e.row().approval().workSeconds()).sum():null));
  }return List.copyOf(result);
 }
}
