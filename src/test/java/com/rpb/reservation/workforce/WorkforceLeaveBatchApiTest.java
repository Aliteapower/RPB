package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceLeaveBatchController;
import com.rpb.reservation.workforce.leave.LeaveBatchService;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
class WorkforceLeaveBatchApiTest {
 @Test void batchRequiresIdentityAndSameOriginProtection()throws Exception{
  var actors=mock(CurrentActorProvider.class);var service=mock(LeaveBatchService.class);var mvc=MockMvcBuilders.standaloneSetup(new WorkforceLeaveBatchController(actors,service)).build();var url="/api/v1/stores/"+UUID.randomUUID()+"/workforce/leave-batch";
  when(actors.currentActor()).thenReturn(Optional.empty());
  mvc.perform(get(url+"?from=2026-10-29&to=2026-10-30")).andExpect(status().isUnauthorized());
  mvc.perform(put(url).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  mvc.perform(put(url).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put(url).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
}
