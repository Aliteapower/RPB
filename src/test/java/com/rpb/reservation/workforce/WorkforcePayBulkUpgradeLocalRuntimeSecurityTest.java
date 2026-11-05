package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayBulkUpgradeController;
import com.rpb.reservation.workforce.payroll.governance.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(WorkforcePayBulkUpgradeController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforcePayBulkUpgradeLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean PayChangeBulkService bulk;@MockBean TemplateUpgradeService upgrades;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void preciseRoutesAreOpenButUnknownMethodsAndDescendantsRemainClosed()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";UUID id=UUID.randomUUID();
  mvc.perform(get(base+"/pay-template-library/"+id+"/upgrade-preview").param("adoptionId",UUID.randomUUID().toString()).param("releaseVersion","2").param("effectiveDate","2026-11-01")).andExpect(status().isOk());
  for(var path:List.of("/pay-change-bulk-actions","/pay-change-requests/"+id+"/upgrade")){
   mvc.perform(put(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());
   mvc.perform(post(base+path)).andExpect(status().isForbidden());mvc.perform(delete(base+path)).andExpect(status().isForbidden());mvc.perform(put(base+path+"/private")).andExpect(status().isForbidden());
  }
  mvc.perform(get(base+"/pay-template-library/"+id+"/upgrade-preview/private")).andExpect(status().isForbidden());verify(upgrades).preview(any(),any(),eq(id),any(),eq(2L),any());verify(bulk).action(any(),any(),any());
 }
}
