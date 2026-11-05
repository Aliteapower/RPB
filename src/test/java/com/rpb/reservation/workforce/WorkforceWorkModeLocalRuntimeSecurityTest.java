package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceWorkModeController;
import com.rpb.reservation.workforce.workmode.*;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.context.annotation.Import;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.context.TestPropertySource;import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(WorkforceWorkModeController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceWorkModeLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean WorkModeService modes;@MockBean HomeAddressService homes;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void onlyExactReadsAndWritesReachScopedServices()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";var employee=UUID.randomUUID();
  mvc.perform(get(base+"/work-mode")).andExpect(status().isOk());mvc.perform(get(base+"/clock-site")).andExpect(status().isOk());mvc.perform(get(base+"/employees/"+employee+"/work-mode")).andExpect(status().isOk());
  for(var path:List.of("/clock-site","/employees/"+employee+"/work-modes/"+UUID.randomUUID(),"/home-addresses/"+UUID.randomUUID(),"/home-addresses/"+UUID.randomUUID()+"/decision"))mvc.perform(put(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());
  verify(modes).self(any(),any());verify(modes).page(any(),any(),eq(employee));verify(modes).save(any(),any(),eq(employee),any(),any());verify(homes).submit(any(),any(),any(),any());verify(homes).decide(any(),any(),any(),any());
  mvc.perform(delete(base+"/clock-site")).andExpect(status().isForbidden());mvc.perform(post(base+"/home-addresses/"+UUID.randomUUID())).andExpect(status().isForbidden());mvc.perform(put(base+"/work-mode/unsupported").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}
