package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceBulkScheduleController;
import com.rpb.reservation.workforce.scheduling.bulk.*;
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
class WorkforceBulkScheduleApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);BulkSchedulePlanService plans=mock(BulkSchedulePlanService.class);BulkScheduleService service=mock(BulkScheduleService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceBulkScheduleController(actors,plans,service)).build();
 @Test void readsArePrivateAndRequireAuthentication()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());for(String path:List.of("/schedule-bulk/options","/schedule-board?from=2026-10-26"))mvc.perform(get(base+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(plans,service);}
 @Test void previewAndCommitHaveTheSameOriginWriteProtection()throws Exception{for(String path:List.of("/schedule-bulk/preview","/schedule-bulk/"+UUID.randomUUID())){mvc.perform(put(base+path).contentType("application/json").content("{}")).andExpect(status().isForbidden());for(String site:List.of("cross-site","same-site"))mvc.perform(put(base+path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());}verifyNoInteractions(plans,service);}
 @Test void stalePreviewIsAnExplicitPrivateConflictAndBadDatesAre400()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(service.commit(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_BULK_CHANGED",409));mvc.perform(put(base+"/schedule-bulk/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.code").value("WORKFORCE_BULK_CHANGED"));mvc.perform(get(base+"/schedule-board?from=not-a-date")).andExpect(status().isBadRequest());}

 @Test void staffingEmployeeListIsPrivateAuthenticatedAndValidatesTypedParameters()throws Exception{
  String path=base+"/schedule-staffing/employees?from=2026-10-29&to=2026-10-29&shiftId="+UUID.randomUUID();when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(plans.staffingEmployees(any(),any(),any(),any(),any(),isNull(),eq(""),eq(50),eq(0))).thenReturn(new BulkScheduleDtos.StaffingEmployees(List.of(),0,0,50,0));mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(header().string("Cache-Control","private, no-store"));mvc.perform(get(path.replace("2026-10-29","invalid"))).andExpect(status().isBadRequest());mvc.perform(get(base+"/schedule-staffing/employees?from=2026-10-29")).andExpect(status().isBadRequest());
 }
}
