package com.rpb.reservation.workforce.payroll;
import java.time.LocalDate;import java.util.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
/** Explicit request and preview projections for changing CDAC only. */
public final class CdacBulk {
 private CdacBulk(){}
 public record Selection(UUID companyId,String month,List<UUID> employeeIds,CdacProfile cdac){}
 public record Write(Selection selection,String fingerprint){}
 public record Row(UUID employeeId,String employeeName,UUID sourceProfileId,LocalDate sourceEffectiveFrom,String previousStatus,LocalDate nextEffectiveFrom,String status,String reason){}
 public record Preview(String fingerprint,String month,List<Row> rows,int ready,int unchanged,int blocked){}
 public record Result(Preview preview,List<UUID> profiles){}
}
