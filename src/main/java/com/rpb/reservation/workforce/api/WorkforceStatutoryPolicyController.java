package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.statutory.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceStatutoryPolicyController extends WorkforceEndpointSupport {
 private final StatutoryPolicyService service;
 public WorkforceStatutoryPolicyController(CurrentActorProvider actors,StatutoryPolicyService service){super(actors);this.service=service;}
 @GetMapping("/statutory-policies") public StatutoryPolicies.Page list(@PathVariable UUID storeId){return service.list(actor(),storeId);}
 @PutMapping("/statutory-policies/{id}") public StatutoryPolicies.Policy save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody StatutoryPolicies.Write c){return service.save(actor(),storeId,id,c);}
 @GetMapping("/statutory-policies/{id}/versions") public List<StatutoryPolicies.Policy> versions(@PathVariable UUID storeId,@PathVariable UUID id){return service.versions(actor(),storeId,id);}
 @GetMapping("/statutory-policy-templates/{year}") public StatutoryPolicies.Template template(@PathVariable UUID storeId,@PathVariable int year){return service.template(actor(),storeId,year);}
}
