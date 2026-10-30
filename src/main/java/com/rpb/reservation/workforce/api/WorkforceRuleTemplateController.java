package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;import com.rpb.reservation.workforce.scheduling.rules.RuleTemplateService;import com.rpb.reservation.workforce.scheduling.rules.RuleTemplateRepository;import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/attendance-rule-templates")
public class WorkforceRuleTemplateController extends WorkforceEndpointSupport {
 private final RuleTemplateService service;public WorkforceRuleTemplateController(CurrentActorProvider actors,RuleTemplateService service){super(actors);this.service=service;}
 @GetMapping public RuleTemplateService.Page list(@PathVariable UUID storeId){return service.list(actor(),storeId);}
 @PutMapping("/{id}") public RuleTemplateRepository.Template save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody RuleTemplateService.Write c){return service.save(actor(),storeId,id,c);}
}
