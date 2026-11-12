package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePositionController;
import com.rpb.reservation.workforce.positions.PositionService;
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
class WorkforcePositionApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);PositionService service=mock(PositionService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/positions";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforcePositionController(actors,service)).build();
 @Test void anonymousReadIsPrivateAndUnauthenticated()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(base)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(service);}
 @Test void writesNeedSameOriginCustomHeader()throws Exception{for(String site:List.of("cross-site","same-site"))mvc.perform(put(base+"/"+UUID.randomUUID()).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(base+"/"+UUID.randomUUID()+"/deletion").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void referenceProtectionReturnsExplicitConflict()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(service.delete(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_POSITION_IN_USE",409));mvc.perform(put(base+"/"+UUID.randomUUID()+"/deletion").header("X-RPB-Workforce","1").contentType("application/json").content("{\"expectedVersion\":1}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_POSITION_IN_USE"));}
}
