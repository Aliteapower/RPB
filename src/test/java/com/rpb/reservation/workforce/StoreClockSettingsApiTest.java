package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceStoreClockController;import com.rpb.reservation.workforce.workmode.StoreClockSettingsService;import com.rpb.reservation.walkin.api.*;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.any;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class StoreClockSettingsApiTest {
 @Test void readAndCalibrationRequireActorAndSameOriginWrites()throws Exception{
  var actors=mock(CurrentActorProvider.class);var service=mock(StoreClockSettingsService.class);var mvc=MockMvcBuilders.standaloneSetup(new WorkforceStoreClockController(actors,service)).build();var base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/clock-site";
  when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(base+"/settings")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  for(var path:List.of("/settings","/check")){mvc.perform(put(base+path).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(base+path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());}
  verifyNoInteractions(service);mvc.perform(put(base+"/check").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));verify(service).check(any(),any(),any());
 }
}
