package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.application.WorkforceHistoryService;
import com.rpb.reservation.workforce.attendance.AttendanceQueryService;
import com.rpb.reservation.workforce.attendance.AttendanceDtos;
import com.rpb.reservation.workforce.attendance.daily.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.payroll.segment.*;
import com.rpb.reservation.workforce.payroll.supplemental.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;
import org.springframework.web.bind.annotation.*;

/** GET-only historical surface. No operational services or commands are exposed. */
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/history")
public class WorkforceHistoryController extends WorkforceEndpointSupport {
 private final WorkforceHistoryService history;private final AttendanceQueryService attendance;private final AttendanceDailyService daily;private final PayslipService payslips;private final SegmentService segments;private final SupplementalService supplements;
 public WorkforceHistoryController(CurrentActorProvider actors,WorkforceHistoryService history,AttendanceQueryService attendance,AttendanceDailyService daily,PayslipService payslips,SegmentService segments,SupplementalService supplements){super(actors);this.history=history;this.attendance=attendance;this.daily=daily;this.payslips=payslips;this.segments=segments;this.supplements=supplements;}
 @GetMapping("/context") public WorkforceHistoryService.Context context(@PathVariable UUID storeId){return history.context(actor(),storeId);}
 @GetMapping("/attendance") public AttendanceDtos.AttendancePage attendance(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) UUID employeeId,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){return attendance.historyList(actor(),storeId,from,to,employeeId,limit,offset);}
 @GetMapping("/attendance/{id}") public AttendanceDtos.Detail attendance(@PathVariable UUID storeId,@PathVariable UUID id){return attendance.historyDetail(actor(),storeId,id);}
 @GetMapping("/attendance-month") public AttendanceDailyDtos.MonthPage month(@PathVariable UUID storeId,@RequestParam YearMonth month,@RequestParam(defaultValue="30") int limit,@RequestParam(defaultValue="0") int offset){return daily.historyMonth(actor(),storeId,month,limit,offset);}
 @GetMapping("/payslips") public List<PayrollDtos.PayslipSummary> payslips(@PathVariable UUID storeId,@RequestParam int year){return payslips.history(actor(),storeId,year,false,null);}
 @GetMapping("/batches") public List<PayslipService.HistoryBatch> batches(@PathVariable UUID storeId,@RequestParam int year,@RequestParam UUID companyId){return payslips.historyBatches(actor(),storeId,year,companyId);}
 @GetMapping("/payslips/{id}") public PayrollDtos.Payslip payslip(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) UUID employeeId){if(!Set.of("mine","managed").contains(scope))throw WorkforceException.invalid();return payslips.historyDetail(actor(),storeId,id,"managed".equals(scope),employeeId);}
 private boolean managed(String scope){if(!Set.of("mine","managed").contains(scope))throw WorkforceException.invalid();return "managed".equals(scope);}
 @GetMapping("/segments") public List<SegmentDtos.Slip> segments(@PathVariable UUID storeId,@RequestParam int year,@RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) UUID companyId){return segments.history(actor(),storeId,year,managed(scope),companyId);}
 @GetMapping("/segments/{id}") public SegmentDtos.Slip segment(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam(defaultValue="mine") String scope){return segments.historyDetail(actor(),storeId,id,managed(scope));}
 @GetMapping("/supplements") public List<SupplementalDtos.Payslip> supplements(@PathVariable UUID storeId,@RequestParam int year,@RequestParam(defaultValue="mine") String scope,@RequestParam(required=false) UUID companyId){return supplements.history(actor(),storeId,year,managed(scope),companyId);}
 @GetMapping("/supplements/{id}") public SupplementalDtos.Payslip supplement(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam(defaultValue="mine") String scope){return supplements.historyDetail(actor(),storeId,id,managed(scope));}
}
