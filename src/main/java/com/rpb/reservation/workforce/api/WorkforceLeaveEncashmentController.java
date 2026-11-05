package com.rpb.reservation.workforce.api;

import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.leave.encashment.*;
import static com.rpb.reservation.workforce.leave.encashment.LeaveEncashmentDtos.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceLeaveEncashmentController extends WorkforceEndpointSupport {
    private final LeaveEncashmentService service;
    public WorkforceLeaveEncashmentController(CurrentActorProvider actors,LeaveEncashmentService service) {super(actors);this.service=service;}
    @GetMapping("/leave-encashments") public List<Document> list(@PathVariable UUID storeId,@RequestParam UUID employeeId){return service.list(actor(),storeId,employeeId);}
    @GetMapping("/leave-encashments/context") public Context context(@PathVariable UUID storeId,@RequestParam UUID employeeId,@RequestParam int year){return service.context(actor(),storeId,employeeId,year);}
    @PostMapping("/leave-encashments/preview") public Quote preview(@PathVariable UUID storeId,@RequestBody Write c){return service.preview(actor(),storeId,c);}
    @PutMapping("/leave-encashments/{id}") public Document create(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Write c){return service.create(actor(),storeId,id,c);}
}
