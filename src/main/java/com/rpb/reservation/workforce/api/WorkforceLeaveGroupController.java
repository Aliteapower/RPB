package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.leave.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/leave-groups")
public class WorkforceLeaveGroupController extends WorkforceEndpointSupport {
 private final LeaveGroupService service;
 public WorkforceLeaveGroupController(CurrentActorProvider actors,LeaveGroupService service){super(actors);this.service=service;}
 @GetMapping public LeaveGroupService.Page list(@PathVariable UUID storeId,@RequestParam LocalDate from,@RequestParam LocalDate to){return service.list(actor(),storeId,from,to);}
 @PutMapping("/{id}/decision") public List<LeaveDtos.Leave> decide(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody LeaveGroupService.Decision request){return service.decide(actor(),storeId,id,request);}
}
