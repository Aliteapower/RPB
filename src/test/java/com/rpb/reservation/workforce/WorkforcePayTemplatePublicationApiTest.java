package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayTemplatePublicationController;
import com.rpb.reservation.workforce.payroll.governance.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WorkforcePayTemplatePublicationApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);TemplatePublicationService publications=mock(TemplatePublicationService.class);TemplateAdoptionService adoptions=mock(TemplateAdoptionService.class);
 UUID store=UUID.randomUUID(),master=UUID.randomUUID();String base="/api/v1/stores/"+store+"/workforce";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforcePayTemplatePublicationController(actors,publications,adoptions)).build();
 void actor(){when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));}
 @Test void authenticationAndRequestProtectionApplyToAllRoutes()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());for(var path:List.of("/pay-template-library","/pay-template-publication-directory","/pay-template-masters","/pay-template-library/"+master+"/preview?releaseVersion=1"))mvc.perform(get(base+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));for(var path:List.of("/pay-template-masters/"+master,"/pay-template-masters/"+master+"/publish","/pay-template-library/"+master+"/enable")){mvc.perform(put(base+path).contentType("application/json").content("{}")).andExpect(status().isForbidden());for(var site:List.of("same-site","cross-site"))mvc.perform(put(base+path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());}verifyNoInteractions(publications,adoptions);}
 @Test void previewBindsFixedVersionAndErrorsStayStable()throws Exception{actor();when(adoptions.preview(any(),eq(store),eq(master),eq(2L))).thenThrow(WorkforceException.missing());mvc.perform(get(base+"/pay-template-library/"+master+"/preview?releaseVersion=2")).andExpect(status().isNotFound());verify(adoptions).preview(any(),eq(store),eq(master),eq(2L));when(adoptions.enable(any(),any(),any(),any())).thenThrow(WorkforceException.conflict());mvc.perform(put(base+"/pay-template-library/"+master+"/enable").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_CONFLICT"));when(publications.drafts(any(),any())).thenThrow(WorkforceException.forbidden());mvc.perform(get(base+"/pay-template-masters")).andExpect(status().isForbidden());mvc.perform(get(base+"/pay-template-library/"+master+"/preview")).andExpect(status().isBadRequest());}
 @Test void publicationDirectoryUsesDedicatedAuthorizedContract()throws Exception{actor();var company=UUID.randomUUID();when(publications.directory(any(),eq(store))).thenReturn(new TemplatePublicationDtos.PublicationDirectory(List.of(new com.rpb.reservation.workforce.application.WorkforceDtos.Option(company,"No stores")),List.of()));mvc.perform(get(base+"/pay-template-publication-directory")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.companies[0].id").value(company.toString())).andExpect(jsonPath("$.stores").isEmpty());when(publications.directory(any(),eq(store))).thenThrow(WorkforceException.forbidden());mvc.perform(get(base+"/pay-template-publication-directory")).andExpect(status().isForbidden());}
}
