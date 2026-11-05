package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.governance.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforcePayBulkUpgradeController extends WorkforceEndpointSupport {
 private final PayChangeBulkService bulk;private final TemplateUpgradeService upgrades;
 public WorkforcePayBulkUpgradeController(CurrentActorProvider actors,PayChangeBulkService bulk,TemplateUpgradeService upgrades){super(actors);this.bulk=bulk;this.upgrades=upgrades;}
 @PutMapping("/pay-change-bulk-actions") public PayChangeBulkDtos.Response bulk(@PathVariable UUID storeId,@RequestBody PayChangeBulkDtos.Command c){return bulk.action(actor(),storeId,c);}
 @GetMapping("/pay-template-library/{id}/upgrade-preview") public TemplateUpgradeDtos.Preview preview(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam UUID adoptionId,@RequestParam long releaseVersion,@RequestParam LocalDate effectiveDate){return upgrades.preview(actor(),storeId,id,adoptionId,releaseVersion,effectiveDate);}
 @PutMapping("/pay-change-requests/{id}/upgrade") public PayChangeDtos.Request upgrade(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody TemplateUpgradeDtos.UpgradeWrite c){return upgrades.save(actor(),storeId,id,c);}
}
