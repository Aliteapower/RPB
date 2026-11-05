package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.scheduling.*;
import static com.rpb.reservation.workforce.scheduling.ScheduleDeletionDtos.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/schedule/{id}/deletion")
public class WorkforceScheduleDeletionController extends WorkforceEndpointSupport {
 private final ScheduleDeletionService service;
 public WorkforceScheduleDeletionController(CurrentActorProvider actors,ScheduleDeletionService service){super(actors);this.service=service;}
 @PutMapping public Result delete(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Request request){return service.delete(actor(),storeId,id,request);}
}
