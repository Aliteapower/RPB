package com.rpb.reservation.workforce.payroll;
import java.time.LocalDate;import java.util.*;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
/** Explicit request and preview projections for changing CDAC only. */
public final class CdacBulk {
 private CdacBulk(){}
 public record Selection(UUID companyId,String month,List<UUID> employeeIds,CdacProfile cdac){}
 public record Write(Selection selection,String fingerprint,String reason){public Write(Selection selection,String fingerprint){this(selection,fingerprint,null);}}
 public record Row(UUID employeeId,String employeeName,UUID sourceProfileId,LocalDate sourceEffectiveFrom,String previousStatus,LocalDate nextEffectiveFrom,String status,String reason){}
 public record Preview(String fingerprint,String month,List<Row> rows,int ready,int unchanged,int blocked){}
 public record RequestResult(UUID employeeId,UUID requestId,String status,String error){}
 public record Result(Preview preview,List<UUID> profiles,List<RequestResult> requests){public Result{profiles=List.copyOf(profiles);requests=requests==null?List.of():List.copyOf(requests);}public Result(Preview preview,List<UUID> profiles){this(preview,profiles,List.of());}}
}
