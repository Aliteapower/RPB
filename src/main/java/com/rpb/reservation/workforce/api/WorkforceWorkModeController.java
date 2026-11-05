package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.workmode.*;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceWorkModeController extends WorkforceEndpointSupport {
 private final WorkModeService modes;private final HomeAddressService homes;
 public WorkforceWorkModeController(CurrentActorProvider actors,WorkModeService modes,HomeAddressService homes){super(actors);this.modes=modes;this.homes=homes;}
 @GetMapping("/work-mode") public WorkModePage self(@PathVariable UUID storeId){return modes.self(actor(),storeId);}
 @GetMapping("/employees/{employeeId}/work-mode") public WorkModePage employee(@PathVariable UUID storeId,@PathVariable UUID employeeId){return modes.page(actor(),storeId,employeeId);}
 @PutMapping("/employees/{employeeId}/work-modes/{id}") public Assignment save(@PathVariable UUID storeId,@PathVariable UUID employeeId,@PathVariable UUID id,@RequestBody AssignmentWrite c){return modes.save(actor(),storeId,employeeId,id,c);}
 @GetMapping("/clock-site") public Site site(@PathVariable UUID storeId){return modes.site(actor(),storeId);}
 @PutMapping("/clock-site") public Site site(@PathVariable UUID storeId,@RequestBody SiteWrite c){return modes.saveSite(actor(),storeId,c);}
 @PutMapping("/home-addresses/{id}") public HomeAddress submit(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody HomeWrite c){return homes.submit(actor(),storeId,id,c);}
 @PutMapping("/home-addresses/{id}/decision") public HomeAddress decide(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody HomeDecision c){return homes.decide(actor(),storeId,id,c);}
}
