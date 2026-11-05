package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.approval.explanations.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce/review-explanations")
public class WorkforceReviewExplanationController extends WorkforceEndpointSupport {
    private final ReviewExplanationService service;
    public WorkforceReviewExplanationController(CurrentActorProvider actors,ReviewExplanationService service){super(actors);this.service=service;}
    @GetMapping public ReviewExplanationService.Page list(@PathVariable UUID storeId){return service.list(actor(),storeId);}
    @PutMapping("/{id}") public ReviewExplanationRepository.Item save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody ReviewExplanationService.Write c){return service.save(actor(),storeId,id,c);}
}
