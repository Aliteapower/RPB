package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceEmployeeLoginController;
import com.rpb.reservation.workforce.application.WorkforceEmployeeLoginService;
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
@WebMvcTest(WorkforceEmployeeLoginController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceEmployeeLoginSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean WorkforceEmployeeLoginService explanations;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void exactPathsReachScopedApplicationButOtherMethodsRemainDenied()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/employees/"+UUID.randomUUID()+"/login-account";
  mvc.perform(get(base)).andExpect(status().isOk());verify(explanations).get(any(),any(),any());
  mvc.perform(put(base).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(explanations).reset(any(),any(),any(),any());
  mvc.perform(delete(base)).andExpect(status().isForbidden());mvc.perform(post(base)).andExpect(status().isForbidden());
 }
}
