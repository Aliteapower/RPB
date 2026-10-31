package com.rpb.reservation.workforce.api;
import com.rpb.reservation.workforce.scheduling.restswap.*;import static com.rpb.reservation.workforce.scheduling.restswap.RestSwapDtos.*;import com.rpb.reservation.walkin.api.CurrentActorProvider;import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/rest-swaps")
public class WorkforceRestSwapController extends WorkforceEndpointSupport {
 private final RestSwapPlanService plans;private final RestSwapService service;
 public WorkforceRestSwapController(CurrentActorProvider actors,RestSwapPlanService plans,RestSwapService service){super(actors);this.plans=plans;this.service=service;}
 @PutMapping("/preview") public Preview preview(@PathVariable UUID storeId,@RequestBody Request request){return plans.preview(actor(),storeId,request);}
 @PutMapping("/{id}") public Result commit(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Commit request){return service.commit(actor(),storeId,id,request);}
}
