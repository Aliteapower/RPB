package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceLeaveGroupController;
import com.rpb.reservation.workforce.leave.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@WebMvcTest(WorkforceLeaveGroupController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceLeaveGroupLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean LeaveGroupService service;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void exactReadAndWritePathsReachScopedApplicationButOtherMethodsRemainDenied()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
  mvc.perform(get(base+"/leave-groups?from=2026-10-29&to=2026-10-30")).andExpect(status().isOk());verify(service).list(any(),any(),any(),any());
  mvc.perform(put(base+"/leave-groups/"+UUID.randomUUID()+"/decision").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(service).decide(any(),any(),any(),any());
  mvc.perform(delete(base+"/leave-groups")).andExpect(status().isForbidden());mvc.perform(post(base+"/leave-groups")).andExpect(status().isForbidden());

 }
 @Test void identityAndSameOriginRemainRequired()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.empty());String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/leave-groups";
  mvc.perform(get(base+"?from=2026-10-29&to=2026-10-30")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  mvc.perform(put(base+"/"+UUID.randomUUID()+"/decision").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put(base+"/"+UUID.randomUUID()+"/decision").header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}

