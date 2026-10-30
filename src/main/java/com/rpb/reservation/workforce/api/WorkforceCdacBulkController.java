package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.CdacBulkService;
import static com.rpb.reservation.workforce.payroll.CdacBulk.*;
import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/pay-profiles/cdac-bulk")
public class WorkforceCdacBulkController extends WorkforceEndpointSupport {
 private final CdacBulkService service;
 public WorkforceCdacBulkController(CurrentActorProvider actors,CdacBulkService service){super(actors);this.service=service;}
 @PutMapping("/preview") public Preview preview(@PathVariable UUID storeId,@RequestBody Selection c){return service.preview(actor(),storeId,c);}
 @PutMapping("/{commandId}") public Result apply(@PathVariable UUID storeId,@PathVariable UUID commandId,@RequestBody Write c){return service.apply(actor(),storeId,commandId,c);}
}
