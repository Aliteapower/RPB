package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceScheduleDeletionController;
import com.rpb.reservation.workforce.scheduling.*;
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
class WorkforceScheduleDeletionApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);ScheduleDeletionService service=mock(ScheduleDeletionService.class);String path="/api/v1/stores/"+UUID.randomUUID()+"/workforce/schedule/"+UUID.randomUUID()+"/deletion";MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceScheduleDeletionController(actors,service)).build();
 @Test void writesRequireSameOriginHeaderAndAuthentication()throws Exception{
  mvc.perform(put(path).contentType("application/json").content("{\"expectedVersion\":2}")).andExpect(status().isForbidden());mvc.perform(put(path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(put(path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(service);
 }
 @Test void usedScheduleIsPrivateConflictAndScopedPermissionFailureIs403()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(service.delete(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_ACTIVE_ATTENDANCE",409));mvc.perform(put(path).header("X-RPB-Workforce","1").contentType("application/json").content("{\"expectedVersion\":1}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_ACTIVE_ATTENDANCE")).andExpect(header().string("Cache-Control","private, no-store"));doThrow(WorkforceException.forbidden()).when(service).delete(any(),any(),any(),any());mvc.perform(put(path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}
