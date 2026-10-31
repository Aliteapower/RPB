package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.payroll.governance.*;
import static com.rpb.reservation.workforce.payroll.governance.TemplatePublicationDtos.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforcePayTemplatePublicationController extends WorkforceEndpointSupport {
 private final TemplatePublicationService publications;private final TemplateAdoptionService adoptions;
 public WorkforcePayTemplatePublicationController(CurrentActorProvider actors,TemplatePublicationService publications,TemplateAdoptionService adoptions){super(actors);this.publications=publications;this.adoptions=adoptions;}
 @GetMapping("/pay-template-masters") public List<Draft> drafts(@PathVariable UUID storeId){return publications.drafts(actor(),storeId);}
 @GetMapping("/pay-template-publication-directory") public PublicationDirectory directory(@PathVariable UUID storeId){return publications.directory(actor(),storeId);}
 @PutMapping("/pay-template-masters/{id}") public Draft save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DraftWrite c){return publications.save(actor(),storeId,id,c);}
 @PutMapping("/pay-template-masters/{id}/publish") public Release publish(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Publish c){return publications.publish(actor(),storeId,id,c);}
 @GetMapping("/pay-template-library") public List<LibraryEntry> library(@PathVariable UUID storeId){return adoptions.library(actor(),storeId);}
 @GetMapping("/pay-template-library/{id}/preview") public Preview preview(@PathVariable UUID storeId,@PathVariable UUID id,@RequestParam long releaseVersion){return adoptions.preview(actor(),storeId,id,releaseVersion);}
 @PutMapping("/pay-template-library/{id}/enable") public Adoption enable(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody Enable c){return adoptions.enable(actor(),storeId,id,c);}
}
