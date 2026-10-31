package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/payroll-workbench")
public class WorkforcePayrollWorkbenchController extends WorkforceEndpointSupport {
 private final PayrollWorkbenchService workbench;
 public WorkforcePayrollWorkbenchController(CurrentActorProvider actors,PayrollWorkbenchService workbench){super(actors);this.workbench=workbench;}
 @GetMapping public PayrollWorkbenchDtos.Workbench read(@PathVariable UUID storeId,@RequestParam UUID companyId,@RequestParam String month,@RequestParam(required=false) Long expectedVersion,@RequestParam(required=false) UUID batchId){return workbench.read(actor(),storeId,companyId,month,expectedVersion,batchId);}
}
