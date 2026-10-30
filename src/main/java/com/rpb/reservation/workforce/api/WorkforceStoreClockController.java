package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.workmode.StoreClockSettingsService;
import static com.rpb.reservation.workforce.workmode.StoreClockSettings.*;
import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/clock-site")
public class WorkforceStoreClockController extends WorkforceEndpointSupport {
 private final StoreClockSettingsService service;
 public WorkforceStoreClockController(CurrentActorProvider actors,StoreClockSettingsService service){super(actors);this.service=service;}
 @GetMapping("/settings") public Settings get(@PathVariable UUID storeId){return service.get(actor(),storeId);}
 @PutMapping("/settings") public Settings save(@PathVariable UUID storeId,@RequestBody Write c){return service.save(actor(),storeId,c);}
 @PutMapping("/check") public CheckResult check(@PathVariable UUID storeId,@RequestBody CheckWrite c){return service.check(actor(),storeId,c);}
}
