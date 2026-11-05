package com.rpb.reservation.workforce.api;
import com.rpb.reservation.workforce.payroll.*;import com.rpb.reservation.walkin.api.CurrentActorProvider;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforcePayPlanEstimateController extends WorkforceEndpointSupport {
 private final PayPlanEstimateService service;public WorkforcePayPlanEstimateController(CurrentActorProvider actors,PayPlanEstimateService service){super(actors);this.service=service;}
 @PostMapping("/pay-plan-estimate") public PayPlanEstimateDtos.Result estimate(@PathVariable UUID storeId,@RequestBody PayPlanEstimateDtos.Request request){return service.estimate(actor(),storeId,request);}
}
