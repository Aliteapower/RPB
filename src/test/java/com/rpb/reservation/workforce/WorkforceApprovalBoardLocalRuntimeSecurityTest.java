package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceApprovalBoardController;
import com.rpb.reservation.workforce.approval.*;
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
@WebMvcTest(WorkforceApprovalBoardController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceApprovalBoardLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean ApprovalBoardService board;@MockBean ApprovalBatchService batches;@MockBean ApprovalReadinessService readiness;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void exactReadAndWritePathsReachScopedApplicationButOtherMethodsRemainDenied()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
  mvc.perform(get(base+"/approval-board?from=2026-10-28&to=2026-10-28")).andExpect(status().isOk());verify(board).board(any(),any(),any(),eq(50),eq(0));
  mvc.perform(get(base+"/approval-readiness?companyId="+UUID.randomUUID()+"&month=2026-10")).andExpect(status().isOk());verify(readiness).check(any(),any(),any(),any());
  mvc.perform(put(base+"/approval-board/preview").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(board).preview(any(),any(),any());
  mvc.perform(put(base+"/approval-board/commit").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(batches).commit(any(),any(),any());
  mvc.perform(delete(base+"/approval-board")).andExpect(status().isForbidden());mvc.perform(post(base+"/approval-board")).andExpect(status().isForbidden());

 }
 @Test void anonymousAndCrossSiteAreRejected()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.empty());String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
  mvc.perform(get(base+"/approval-board?from=2026-10-28&to=2026-10-28")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  mvc.perform(get(base+"/approval-readiness?companyId="+UUID.randomUUID()+"&month=2026-10")).andExpect(status().isUnauthorized());
  mvc.perform(put(base+"/approval-board/commit").header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put(base+"/approval-board/preview").contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}

