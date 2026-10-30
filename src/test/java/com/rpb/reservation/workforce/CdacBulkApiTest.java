package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceCdacBulkController;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class CdacBulkApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);CdacBulkService service=mock(CdacBulkService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/pay-profiles/cdac-bulk";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceCdacBulkController(actors,service)).build();
 @Test void writesRequireSameOriginHeaderAndActor()throws Exception{
  for(String tail:List.of("preview",UUID.randomUUID().toString())){
   mvc.perform(put(base+"/"+tail).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   mvc.perform(put(base+"/"+tail).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
   mvc.perform(put(base+"/"+tail).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  }verifyNoInteractions(service);
 }
 @Test void changedPreviewReturnsStableConflict()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  when(service.apply(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_CDAC_BULK_CHANGED",409));
  mvc.perform(put(base+"/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_CDAC_BULK_CHANGED"));
 }
}
