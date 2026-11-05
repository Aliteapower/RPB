package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayrollConfigurationController;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class WorkforcePayrollConfigurationApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);EmployeePaySummaryService summaries=mock(EmployeePaySummaryService.class);PositionSalaryDefaultsService defaults=mock(PositionSalaryDefaultsService.class);
 UUID store=UUID.randomUUID(),company=UUID.randomUUID(),employee=UUID.randomUUID();String base="/api/v1/stores/"+store+"/workforce";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforcePayrollConfigurationController(actors,summaries,defaults)).build();
 void actor(){when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));}
 @Test void readsRequireAuthenticationAndNeverCache()throws Exception{when(actors.currentActor()).thenReturn(Optional.empty());for(var path:List.of("/employees/"+employee+"/pay-summary?companyId="+company+"&on=2026-10-30","/position-salary-defaults?companyId="+company+"&positionId="+UUID.randomUUID(),"/pay-profile-preview?companyId="+company+"&employeeId="+employee+"&on=2026-10-30"))mvc.perform(get(base+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(summaries,defaults);}
 @Test void summaryBindsExplicitEmployeeCompanyAndDate()throws Exception{actor();when(summaries.summary(any(),eq(store),eq(employee),eq(company),eq(LocalDate.of(2026,10,30)))).thenReturn(new PayrollDtos.EmployeePaySummary(employee,company,LocalDate.of(2026,10,30),List.of()));mvc.perform(get(base+"/employees/"+employee+"/pay-summary?companyId="+company+"&on=2026-10-30")).andExpect(status().isOk()).andExpect(jsonPath("$.employeeId").value(employee.toString())).andExpect(header().string("Cache-Control","private, no-store"));}
 @Test void defaultWritesRejectCrossSiteOrMissingHeaderBeforeService()throws Exception{for(var site:List.of("cross-site","same-site"))mvc.perform(put(base+"/position-salary-defaults/"+UUID.randomUUID()).header("Sec-Fetch-Site",site).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(base+"/position-salary-defaults/"+UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(defaults);}
 @Test void conflictsAndPermissionFailuresUseStableStatusCodes()throws Exception{actor();when(defaults.save(any(),any(),any(),any())).thenThrow(WorkforceException.conflict());mvc.perform(put(base+"/position-salary-defaults/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_CONFLICT"));when(summaries.summary(any(),any(),any(),any(),any())).thenThrow(WorkforceException.forbidden());mvc.perform(get(base+"/employees/"+employee+"/pay-summary?companyId="+company+"&on=2026-10-30")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKFORCE_FORBIDDEN"));}
}
