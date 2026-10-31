package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.scheduling.adjustment.*;
import static com.rpb.reservation.workforce.scheduling.adjustment.BulkAdjustmentDtos.*;
import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/schedule-adjustments")
public class WorkforceBulkAdjustmentController extends WorkforceEndpointSupport {
 private final BulkAdjustmentPlanService plans;private final BulkAdjustmentService service;
 public WorkforceBulkAdjustmentController(CurrentActorProvider actors,BulkAdjustmentPlanService plans,BulkAdjustmentService service){super(actors);this.plans=plans;this.service=service;}
 @PutMapping("/preview") public Preview preview(@PathVariable UUID storeId,@RequestBody Request request){return plans.preview(actor(),storeId,request);}
 @PutMapping("/{id}") public Result commit(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Commit request){return service.commit(actor(),storeId,id,request);}
}
