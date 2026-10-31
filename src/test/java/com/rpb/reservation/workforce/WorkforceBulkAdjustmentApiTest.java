package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceBulkAdjustmentController;
import com.rpb.reservation.workforce.scheduling.adjustment.*;
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
class WorkforceBulkAdjustmentApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);BulkAdjustmentPlanService plans=mock(BulkAdjustmentPlanService.class);BulkAdjustmentService service=mock(BulkAdjustmentService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/schedule-adjustments";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceBulkAdjustmentController(actors,plans,service)).build();
 @Test void previewAndCommitAreAuthenticatedPrivateAndProtectedAgainstCrossOriginWrites()throws Exception{for(String path:List.of(base+"/preview",base+"/"+UUID.randomUUID())){mvc.perform(put(path).contentType("application/json").content("{}")).andExpect(status().isForbidden());for(String site:List.of("cross-site","same-site"))mvc.perform(put(path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));}verifyNoInteractions(plans,service);}
 @Test void changedPreviewRetainsStableConflictCodeAndMalformedDatesAre400()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(service.commit(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_ADJUST_CHANGED",409));mvc.perform(put(base+"/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_ADJUST_CHANGED")).andExpect(header().string("Cache-Control","private, no-store"));mvc.perform(put(base+"/preview").header("X-RPB-Workforce","1").contentType("application/json").content("{\"date\":\"invalid\"}")).andExpect(status().isBadRequest());verifyNoInteractions(plans);}
}
