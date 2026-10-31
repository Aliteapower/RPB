package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.time.*;import java.util.*;
/** Monthly eligibility is never inferred from absence of a finding or from missing punches. */
public final class FullAttendanceBonus {
 private FullAttendanceBonus(){}
 public static boolean configured(PayProfile p){return p!=null&&p.rule().items().stream().anyMatch(i->i.kind().equals("ow_attendance"));}
 public static String status(List<SourceRef> refs,YearMonth month,LocalDate cutoff){
  if(cutoff.isBefore(month.atEndOfMonth()))return "deferred";
  var relevant=refs.stream().filter(r->r.payable()&&YearMonth.from(r.workDate()).equals(month)).toList();
  if(relevant.isEmpty()||relevant.stream().anyMatch(r->r.evidence()==null||!"current".equals(r.evidence().approvalState())||!Set.of("eligible","ineligible").contains(Objects.toString(r.evidence().fullAttendanceStatus(),""))))return "pending";
  return relevant.stream().anyMatch(r->"ineligible".equals(r.evidence().fullAttendanceStatus()))?"ineligible":"eligible";
 }
}
