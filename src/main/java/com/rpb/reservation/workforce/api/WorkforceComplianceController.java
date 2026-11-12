package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import com.rpb.reservation.workforce.compliance.*;import static com.rpb.reservation.workforce.compliance.ComplianceDtos.*;
import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce") public class WorkforceComplianceController extends WorkforceEndpointSupport {
 private final ComplianceService service;public WorkforceComplianceController(CurrentActorProvider actors,ComplianceService service){super(actors);this.service=service;}
 @GetMapping("/compliance-reference") public Reference reference(@PathVariable UUID storeId){return service.reference(actor(),storeId);}
 @GetMapping("/compliance-policies") public List<Policy> policies(@PathVariable UUID storeId){return service.policies(actor(),storeId);}
 @GetMapping("/compliance-policies/{id}/versions") public List<Policy> versions(@PathVariable UUID storeId,@PathVariable UUID id){return service.versions(actor(),storeId,id);}
 @PutMapping("/compliance-policies/{id}") public Policy save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody PolicyWrite c){return service.savePolicy(actor(),storeId,id,c);}
 @GetMapping("/employees/{employeeId}/employment-contracts") public ContractPage contracts(@PathVariable UUID storeId,@PathVariable UUID employeeId){return service.contracts(actor(),storeId,employeeId);}
 @PutMapping("/employees/{employeeId}/employment-contracts/{id}") public Contract contract(@PathVariable UUID storeId,@PathVariable UUID employeeId,@PathVariable UUID id,@RequestBody ContractWrite c){return service.saveContract(actor(),storeId,employeeId,id,c);}
}
