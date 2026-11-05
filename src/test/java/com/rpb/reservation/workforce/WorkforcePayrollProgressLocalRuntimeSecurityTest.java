package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayrollProgressController;
import com.rpb.reservation.workforce.payroll.*;import com.rpb.reservation.walkin.api.*;import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.context.annotation.Import;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.context.TestPropertySource;import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(WorkforcePayrollProgressController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforcePayrollProgressLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean PayrollProgressService progress;@MockBean PayrollBulkService bulk;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void exactProgressAndBulkRoutesReachTheirOwnAuthorizationLayer()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));var base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";var id=UUID.randomUUID();
  mvc.perform(get(base+"/payroll-batches/"+id+"/changes")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
  mvc.perform(put(base+"/payroll-batch-operations/"+id).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(progress).changes(any(),any(),eq(id));verify(bulk).execute(any(),any(),eq(id),any());
  mvc.perform(post(base+"/payroll-batch-operations/"+id)).andExpect(status().isForbidden());mvc.perform(delete(base+"/payroll-batches/"+id+"/changes")).andExpect(status().isForbidden());mvc.perform(get(base+"/payroll-batches/"+id+"/changes/private")).andExpect(status().isForbidden());
 }
}
