package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforcePayrollController;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class WorkforcePayrollApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);PayrollConfigurationService configuration=mock(PayrollConfigurationService.class);PayrollBatchService batches=mock(PayrollBatchService.class);PayrollPaymentService payments=mock(PayrollPaymentService.class);PayslipService slips=mock(PayslipService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce",company=UUID.randomUUID().toString();
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforcePayrollController(actors,configuration,batches,payments,slips)).build();
 @Test void payrollAndPublishedSlipReadsRequireActorAndPreventCaching()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.empty());for(String path:List.of("/my-latest-payslip","/payroll-home","/salary-templates?companyId="+company,"/pay-profiles?companyId="+company,"/payroll-batches?companyId="+company,"/payroll-batches/"+UUID.randomUUID(),"/my-payslips?year=2026","/my-payslips/"+UUID.randomUUID()))mvc.perform(get(base+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
 }
 @Test void monetaryWritesRejectCrossSiteAndMissingHeaderBeforeBusinessServices()throws Exception{
  for(String path:List.of("/salary-templates/"+UUID.randomUUID(),"/pay-profiles/"+UUID.randomUUID(),"/payroll-batches/"+UUID.randomUUID(),"/payroll-batches/"+UUID.randomUUID()+"/actions/"+UUID.randomUUID(),"/payroll-batches/"+UUID.randomUUID()+"/payments/"+UUID.randomUUID())){
   for(String site:List.of("same-site","cross-site"))mvc.perform(put(base+path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   mvc.perform(put(base+path).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  }verifyNoInteractions(configuration,batches,payments);
 }
 @Test void sourceChangedIsStable409AndInvalidYearBindingIs400()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(batches.action(any(),any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_SOURCE_CHANGED",409));
  mvc.perform(put(base+"/payroll-batches/"+UUID.randomUUID()+"/actions/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_PAYROLL_SOURCE_CHANGED"));
  mvc.perform(get(base+"/my-payslips?year=wrong")).andExpect(status().isBadRequest());
 }
 @Test void boundedComputationIsRetryable503WithoutChangingResponseContract()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(batches.action(any(),any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_BUSY",503));
  mvc.perform(put(base+"/payroll-batches/"+UUID.randomUUID()+"/actions/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.code").value("WORKFORCE_PAYROLL_BUSY"));
 }
 @Test void allLegacyEconomicWritesExposeSameStableApprovalRequiredConflict()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  when(configuration.saveTemplate(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_APPROVAL_REQUIRED",409));when(configuration.saveProfile(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_APPROVAL_REQUIRED",409));when(batches.input(any(),any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_APPROVAL_REQUIRED",409));
  for(var path:List.of("/salary-templates/"+UUID.randomUUID(),"/pay-profiles/"+UUID.randomUUID(),"/payroll-batches/"+UUID.randomUUID()+"/inputs/"+UUID.randomUUID()))mvc.perform(put(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.code").value("WORKFORCE_PAYROLL_APPROVAL_REQUIRED"));
 }
 @Test void firstMissingCdacDeclarationIsAStableInformationConflict()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(configuration.saveProfile(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED",409));mvc.perform(put(base+"/pay-profiles/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED"));
 }
}
