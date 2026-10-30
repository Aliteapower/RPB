package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.calendar.*;
import com.rpb.reservation.workforce.approval.*;
import static com.rpb.reservation.workforce.leave.LeaveDtos.*;
import static com.rpb.reservation.workforce.calendar.CalendarDtos.*;
import static com.rpb.reservation.workforce.approval.ApprovalDtos.*;
import com.rpb.reservation.workforce.attendance.AttendanceDtos.DecisionWrite;
import java.time.LocalDate;import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceTimekeepingController extends WorkforceEndpointSupport {
 private final LeaveTypeService types;private final LeaveService leaves;private final CalendarService calendar;private final ApprovalService approvals;private final WorkSummaryService summaries;
 public WorkforceTimekeepingController(CurrentActorProvider actors,LeaveTypeService types,LeaveService leaves,CalendarService calendar,ApprovalService approvals,WorkSummaryService summaries){super(actors);this.types=types;this.leaves=leaves;this.calendar=calendar;this.approvals=approvals;this.summaries=summaries;}
 @GetMapping("/leave-types") public List<LeaveType> types(@PathVariable UUID storeId){return types.list(actor(),storeId);}
 @PutMapping("/leave-types/{id}") public LeaveType type(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody LeaveTypeWrite c){return types.save(actor(),storeId,id,c);}
 @GetMapping("/leaves") public List<Leave> leaves(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){return leaves.list(actor(),storeId,from,to);}
 @PutMapping("/leaves/{id}") public Leave leave(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody LeaveWrite c){return leaves.submit(actor(),storeId,id,c);}
 @PutMapping("/leaves/{id}/decision") public Leave decision(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DecisionWrite c){return leaves.decide(actor(),storeId,id,c);}
 @GetMapping("/calendar") public CalendarPage calendar(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){return calendar.list(actor(),storeId,from,to);}
 @PutMapping("/calendar/{id}") public CalendarEvent event(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CalendarWrite c){return calendar.save(actor(),storeId,id,c);}
 @GetMapping("/approvals") public List<ApprovalRow> approvals(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){return approvals.list(actor(),storeId,from,to);}
 @GetMapping("/approvals/{id}") public ApprovalDetail approval(@PathVariable UUID storeId,@PathVariable UUID id){return approvals.detail(actor(),storeId,id);}
 @PutMapping("/approvals/{id}") public Approval approve(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ApprovalWrite c){return approvals.approve(actor(),storeId,id,c);}
 @GetMapping("/work-summary") public Summary summary(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false)UUID companyId){return summaries.summary(actor(),storeId,from,to,companyId);}
}
