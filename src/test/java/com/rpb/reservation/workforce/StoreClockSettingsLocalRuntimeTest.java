package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceStoreClockController;
import com.rpb.reservation.workforce.workmode.StoreClockSettingsService;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(WorkforceStoreClockController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class StoreClockSettingsLocalRuntimeTest {
 @Autowired MockMvc mvc;
 @MockBean CurrentActorProvider actors;
 @MockBean StoreClockSettingsService service;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void onlyExactConfigurationAndCalibrationRoutesReachScopedService()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/clock-site";
  mvc.perform(get(base+"/settings")).andExpect(status().isOk());
  for(String path:List.of("/settings","/check"))mvc.perform(put(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());
  verify(service).get(any(),any());verify(service).save(any(),any(),any());verify(service).check(any(),any(),any());
  mvc.perform(get(base+"/check")).andExpect(status().isForbidden());
  mvc.perform(delete(base+"/settings")).andExpect(status().isForbidden());
  mvc.perform(post(base+"/check")).andExpect(status().isForbidden());
 }
}
