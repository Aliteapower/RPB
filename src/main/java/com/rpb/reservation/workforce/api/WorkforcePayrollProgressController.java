package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.*;
import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforcePayrollProgressController extends WorkforceEndpointSupport {
    private final PayrollProgressService progress;private final PayrollBulkService bulk;
    public WorkforcePayrollProgressController(CurrentActorProvider actors,PayrollProgressService progress,PayrollBulkService bulk){super(actors);this.progress=progress;this.bulk=bulk;}
    @GetMapping("/payroll-batches/{id}/changes")
    public ChangeReport changes(@PathVariable UUID storeId,@PathVariable UUID id){return progress.changes(actor(),storeId,id);}
    @PutMapping("/payroll-batch-operations/{commandId}")
    public BulkResult operation(@PathVariable UUID storeId,@PathVariable UUID commandId,@RequestBody BulkWrite command){return bulk.execute(actor(),storeId,commandId,command);}
}
