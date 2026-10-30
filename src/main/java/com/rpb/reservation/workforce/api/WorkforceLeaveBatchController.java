package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.leave.*;
import java.util.*;
import java.time.LocalDate;
import com.rpb.reservation.workforce.scheduling.SchedulingDtos.SchedulePage;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/leave-batch")
public class WorkforceLeaveBatchController extends WorkforceEndpointSupport {
    private final LeaveBatchService service;
    public WorkforceLeaveBatchController(CurrentActorProvider actors,LeaveBatchService service){super(actors);this.service=service;}
    @GetMapping public SchedulePage options(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){return service.options(actor(),storeId,from,to);}
    @PutMapping public List<LeaveDtos.Leave> submit(@PathVariable UUID storeId,@RequestBody LeaveBatchService.Request request){return service.submit(actor(),storeId,request);}
}
