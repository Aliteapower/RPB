package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.scheduling.transfer.ScheduleTransferService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.workforce.scheduling.transfer.ScheduleTransferService.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/schedule-transfer")
public class WorkforceScheduleTransferController extends WorkforceEndpointSupport {
    private final ScheduleTransferService service;

    public WorkforceScheduleTransferController(CurrentActorProvider actors,ScheduleTransferService service){
        super(actors);
        this.service=service;
    }

    @PutMapping("/preview")
    public Result preview(@PathVariable UUID storeId,@RequestBody Transfer write){
        return service.preview(actor(),storeId,write);
    }

    @PutMapping("/{commandId}")
    public Result commit(@PathVariable UUID storeId,@PathVariable UUID commandId,@RequestBody Transfer write){
        return service.commit(actor(),storeId,commandId,write);
    }
}
