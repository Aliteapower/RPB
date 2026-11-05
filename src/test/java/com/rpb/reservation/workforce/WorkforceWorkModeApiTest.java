package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceWorkModeController;
import com.rpb.reservation.workforce.workmode.*;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.*;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class WorkforceWorkModeApiTest {
 final CurrentActorProvider actors=mock(CurrentActorProvider.class);final WorkModeService modes=mock(WorkModeService.class);final HomeAddressService homes=mock(HomeAddressService.class);final MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceWorkModeController(actors,modes,homes)).build();final String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
 @Test void allReadsRequireActorAndNoCache()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());for(var path:List.of("/work-mode","/clock-site","/employees/"+UUID.randomUUID()+"/work-mode"))mvc.perform(get(base+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));}
 @Test void allWritesRejectCrossSiteAndMissingHeaderBeforeServices()throws Exception{for(var path:List.of("/clock-site","/employees/"+UUID.randomUUID()+"/work-modes/"+UUID.randomUUID(),"/home-addresses/"+UUID.randomUUID(),"/home-addresses/"+UUID.randomUUID()+"/decision")){mvc.perform(put(base+path).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(base+path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());}verifyNoInteractions(modes,homes);}
 @Test void scopedConflictRetainsStableCode()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(homes.decide(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_CONFLICT",409));mvc.perform(put(base+"/home-addresses/"+UUID.randomUUID()+"/decision").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_CONFLICT"));}
}
