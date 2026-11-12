package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceScheduleDeletionController;
import com.rpb.reservation.workforce.scheduling.*;
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
@WebMvcTest(WorkforceScheduleDeletionController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceScheduleDeletionLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean ScheduleDeletionService service;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void exactReadAndWritePathsReachScopedApplicationButOtherMethodsRemainDenied()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
  String path=base+"/schedule/"+UUID.randomUUID()+"/deletion";mvc.perform(put(path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(service).delete(any(),any(),any(),any());mvc.perform(delete(path)).andExpect(status().isForbidden());mvc.perform(post(path)).andExpect(status().isForbidden());

 }
}

