package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.scheduling.bulk.*;
import static com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleDtos.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceBulkScheduleController extends WorkforceEndpointSupport {
 private final BulkSchedulePlanService plans;private final BulkScheduleService service;
 public WorkforceBulkScheduleController(CurrentActorProvider actors,BulkSchedulePlanService plans,BulkScheduleService service){super(actors);this.plans=plans;this.service=service;}
 @GetMapping("/schedule-bulk/options") public Options options(@PathVariable UUID storeId,@RequestParam(required=false) UUID positionId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){return plans.options(actor(),storeId,positionId,search,limit,offset);}
 @GetMapping("/schedule-board") public Board board(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) UUID positionId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){return to==null?plans.board(actor(),storeId,from,positionId,search,limit,offset):plans.monthBoard(actor(),storeId,from,to,positionId,search,limit,offset);}
 @GetMapping("/schedule-staffing/employees") public StaffingEmployees staffingEmployees(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam UUID shiftId,@RequestParam(required=false) UUID positionId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") int offset){return plans.staffingEmployees(actor(),storeId,from,to,shiftId,positionId,search,limit,offset);}
 @PutMapping("/schedule-bulk/preview") public Preview preview(@PathVariable UUID storeId,@RequestBody Request request){return plans.preview(actor(),storeId,request);}
 @PutMapping("/schedule-bulk/{id}") public Result commit(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Commit request){return service.commit(actor(),storeId,id,request);}
}
