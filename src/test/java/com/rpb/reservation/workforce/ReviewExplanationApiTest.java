package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceReviewExplanationController;
import com.rpb.reservation.workforce.approval.explanations.ReviewExplanationService;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class ReviewExplanationApiTest {
    CurrentActorProvider actors=mock(CurrentActorProvider.class);ReviewExplanationService service=mock(ReviewExplanationService.class);
    String path="/api/v1/stores/"+UUID.randomUUID()+"/workforce/review-explanations";
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceReviewExplanationController(actors,service)).build();
    @Test void readRequiresIdentityAndPreventsCaching()throws Exception{
        when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(service);
    }
    @Test void writeRejectsMissingHeaderAndCrossSiteBeforeService()throws Exception{
        mvc.perform(put(path+"/"+UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put(path+"/"+UUID.randomUUID()).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void staleEditKeepsStable409AndNoCache()throws Exception{
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"admin",Set.of(),Set.of(),Set.of())));
        when(service.save(any(),any(),any(),any())).thenThrow(WorkforceException.conflict());
        mvc.perform(put(path+"/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_CONFLICT"));
    }
}
