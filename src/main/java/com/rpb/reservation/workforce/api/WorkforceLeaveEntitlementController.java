package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import com.rpb.reservation.workforce.leave.entitlement.LeaveEntitlementService;import static com.rpb.reservation.workforce.leave.entitlement.LeaveEntitlementDtos.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce") public class WorkforceLeaveEntitlementController extends WorkforceEndpointSupport {
 private final LeaveEntitlementService service;public WorkforceLeaveEntitlementController(CurrentActorProvider actors,LeaveEntitlementService service){super(actors);this.service=service;}
 @GetMapping("/leave-entitlements") public Page page(@PathVariable UUID storeId,@RequestParam(required=false) UUID employeeId,@RequestParam int year,@RequestParam(required=false) UUID companyId){return service.page(actor(),storeId,employeeId,year,companyId);}
 @PutMapping("/employees/{employeeId}/leave-entitlements/{commandId}") public Policy save(@PathVariable UUID storeId,@PathVariable UUID employeeId,@PathVariable UUID commandId,@RequestBody Write c){return service.save(actor(),storeId,employeeId,commandId,c);}
}
