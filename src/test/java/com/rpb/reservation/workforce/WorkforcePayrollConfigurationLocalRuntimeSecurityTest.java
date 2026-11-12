package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayrollConfigurationController;
import com.rpb.reservation.workforce.payroll.*;import com.rpb.reservation.walkin.api.*;import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.util.*;import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.mock.mockito.MockBean;import org.springframework.context.annotation.Import;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.context.TestPropertySource;import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(WorkforcePayrollConfigurationController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforcePayrollConfigurationLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean EmployeePaySummaryService summaries;@MockBean PositionSalaryDefaultsService defaults;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void exactConfigurationRoutesReachTheirOwnAuthorizationLayer()throws Exception{
 when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));var base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";var id=UUID.randomUUID();var company=UUID.randomUUID();
 mvc.perform(get(base+"/employees/"+id+"/pay-summary?companyId="+company+"&on=2026-10-30")).andExpect(status().isOk());
 mvc.perform(get(base+"/position-salary-defaults?companyId="+company+"&positionId="+id)).andExpect(status().isOk());
 mvc.perform(get(base+"/pay-profile-preview?companyId="+company+"&employeeId="+id+"&on=2026-10-30")).andExpect(status().isOk());
 mvc.perform(put(base+"/position-salary-defaults/"+id).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());
 verify(summaries).summary(any(),any(),eq(id),eq(company),any());verify(defaults).save(any(),any(),eq(id),any());
 mvc.perform(post(base+"/position-salary-defaults/"+id)).andExpect(status().isForbidden());mvc.perform(delete(base+"/position-salary-defaults/"+id)).andExpect(status().isForbidden());mvc.perform(get(base+"/pay-profile-preview/private")).andExpect(status().isForbidden());
 }
}
