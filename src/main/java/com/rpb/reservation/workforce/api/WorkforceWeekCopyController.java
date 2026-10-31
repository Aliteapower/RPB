package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.scheduling.copy.*;
import com.rpb.reservation.workforce.scheduling.bulk.BulkScheduleDtos.Result;
import static com.rpb.reservation.workforce.scheduling.copy.WeekCopyDtos.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/schedule-copy-bulk")
public class WorkforceWeekCopyController extends WorkforceEndpointSupport {
 private final WeekCopyPlanService plans;private final WeekCopyService copies;
 public WorkforceWeekCopyController(CurrentActorProvider actors,WeekCopyPlanService plans,WeekCopyService copies){super(actors);this.plans=plans;this.copies=copies;}
 @PutMapping("/preview") public Preview preview(@PathVariable UUID storeId,@RequestBody Request request){return plans.preview(actor(),storeId,request);}
 @PutMapping("/{id}") public Result copy(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Commit request){return copies.commit(actor(),storeId,id,request);}
}
