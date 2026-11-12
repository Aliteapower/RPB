package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceWeekCopyController;
import com.rpb.reservation.workforce.scheduling.copy.*;
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
class WorkforceWeekCopyApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);WeekCopyPlanService plans=mock(WeekCopyPlanService.class);WeekCopyService service=mock(WeekCopyService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceWeekCopyController(actors,plans,service)).build();
 @Test void previewRequiresAuthentication()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(put(base+"/schedule-copy-bulk/preview").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(plans,service);}
 @Test void previewAndCommitHaveTheSameOriginWriteProtection()throws Exception{for(String path:List.of("/schedule-copy-bulk/preview","/schedule-copy-bulk/"+UUID.randomUUID())){mvc.perform(put(base+path).contentType("application/json").content("{}")).andExpect(status().isForbidden());for(String site:List.of("cross-site","same-site"))mvc.perform(put(base+path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());}verifyNoInteractions(plans,service);}
 @Test void stalePreviewIsAnExplicitPrivateConflictAndBadDatesAre400()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(service.commit(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_COPY_CHANGED",409));mvc.perform(put(base+"/schedule-copy-bulk/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.code").value("WORKFORCE_COPY_CHANGED"));when(plans.preview(any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_COPY_TOO_LARGE",400));mvc.perform(put(base+"/schedule-copy-bulk/preview").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WORKFORCE_COPY_TOO_LARGE"));}
}
