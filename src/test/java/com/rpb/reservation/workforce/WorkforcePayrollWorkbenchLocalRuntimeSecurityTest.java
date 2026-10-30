package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayrollWorkbenchController;
import com.rpb.reservation.workforce.payroll.*;import com.rpb.reservation.walkin.api.*;import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.context.annotation.Import;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.context.TestPropertySource;import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(WorkforcePayrollWorkbenchController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforcePayrollWorkbenchLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean PayrollWorkbenchService workbench;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void onlyExactGetReachesPayrollAuthorization()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/payroll-workbench";
  mvc.perform(get(base).param("companyId",UUID.randomUUID().toString()).param("month","2026-10")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));verify(workbench).read(any(),any(),any(),eq("2026-10"),isNull(),isNull());
  mvc.perform(get(base+"/private")).andExpect(status().isForbidden());for(var method:List.of("POST","PUT","DELETE"))mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),base)).andExpect(status().isForbidden());
 }
}
