package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import com.rpb.reservation.workforce.attendancepolicy.AttendancePolicyService;import static com.rpb.reservation.workforce.attendancepolicy.AttendancePolicyDtos.*;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceAttendancePolicyController extends WorkforceEndpointSupport {
 private final AttendancePolicyService service;
 public WorkforceAttendancePolicyController(CurrentActorProvider actors,AttendancePolicyService service){super(actors);this.service=service;}
 @GetMapping("/attendance-policy-defaults") public List<PolicyVersion> defaults(@PathVariable UUID storeId){return service.defaults(actor(),storeId);}
 @PutMapping("/attendance-policy-defaults/{id}") public PolicyVersion defaults(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DefaultWrite c){return service.saveDefault(actor(),storeId,id,c);}
 @GetMapping("/attendance-policy") public PolicyPage self(@PathVariable UUID storeId){return service.self(actor(),storeId);}
 @GetMapping("/employees/{employeeId}/attendance-policy") public PolicyPage employee(@PathVariable UUID storeId,@PathVariable UUID employeeId){return service.page(actor(),storeId,employeeId);}
 @PutMapping("/employees/{employeeId}/attendance-policies/{id}") public PolicyVersion employee(@PathVariable UUID storeId,@PathVariable UUID employeeId,@PathVariable UUID id,@RequestBody AssignmentWrite c){return service.save(actor(),storeId,employeeId,id,c);}
}
