package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.api.*;
import com.rpb.reservation.workforce.workmode.*;
import com.rpb.reservation.workforce.attendance.*;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class WorkforceClockOptionsApiTest {
 @Test void actualClockChoiceAndStableDenialCodeReachExistingProtectedEndpoint()throws Exception{
  var actors=mock(CurrentActorProvider.class);when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));var clock=mock(ClockService.class);var mvc=MockMvcBuilders.standaloneSetup(new WorkforceAttendanceController(actors,null,null,clock,null,null)).build();when(clock.punch(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_CLOCK_MODE_FORBIDDEN",403));
  mvc.perform(put("/api/v1/stores/"+UUID.randomUUID()+"/workforce/clock/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{\"scheduleId\":\""+UUID.randomUUID()+"\",\"action\":\"OUT\",\"mode\":\"field\",\"location\":{\"description\":\"客户 A\"}}"))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKFORCE_CLOCK_MODE_FORBIDDEN")).andExpect(header().string("Cache-Control","private, no-store"));verify(clock).punch(any(),any(),any(),argThat(c->c.mode().equals("field")&&c.location().description().equals("客户 A")));
 }
 @Test void homeReasonsRetainSpecificStatusAndCode()throws Exception{
  var actors=mock(CurrentActorProvider.class);when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));var homes=mock(HomeAddressService.class);var mvc=MockMvcBuilders.standaloneSetup(new WorkforceWorkModeController(actors,mock(WorkModeService.class),homes)).build();
  for(String code:List.of("WORKFORCE_HOME_REQUEST_EXPIRED","WORKFORCE_HOME_EFFECTIVE_DATE_CONFLICT")){doThrow(new WorkforceException(code,409)).when(homes).decide(any(),any(),any(),any());mvc.perform(put("/api/v1/stores/"+UUID.randomUUID()+"/workforce/home-addresses/"+UUID.randomUUID()+"/decision").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code));}
 }
}
