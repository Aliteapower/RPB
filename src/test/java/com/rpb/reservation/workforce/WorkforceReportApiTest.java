package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.api.WorkforceReportController;
import com.rpb.reservation.workforce.reporting.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkforceReportApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);PayrollReportService reports=mock(PayrollReportService.class);PayrollReconciliationService comparison=mock(PayrollReconciliationService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce",query="?month=2026-10&companyIds="+UUID.randomUUID();
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceReportController(actors,reports,comparison)).build();
 void actor(){when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));}
 @Test void reportAndExportRequireIdentityAndHaveNoStore()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.empty());for(String path:List.of("/payroll-report"+query,"/payroll-report/export"+query+"&kind=companies&fingerprint=x"))mvc.perform(get(base+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(reports);
 }
 @Test void csvIsAnAttachmentAndErrorsStayJsonWithNoSensitiveBody()throws Exception{
  actor();when(reports.export(any(),any(),any(),anyList(),any(),any())).thenReturn("\uFEFFa,b\r\n1,2\r\n");
  mvc.perform(get(base+"/payroll-report/export"+query+"&kind=companies&fingerprint=x")).andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8")).andExpect(header().string("Content-Disposition","attachment; filename=\"workforce-companies-2026-10.csv\"")).andExpect(header().string("Cache-Control","private, no-store"));
  when(reports.export(any(),any(),any(),anyList(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_REPORT_CHANGED",409));mvc.perform(get(base+"/payroll-report/export"+query+"&kind=companies&fingerprint=x")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_REPORT_CHANGED"));
 }
 @Test void comparisonRequiresSameOriginHeaderAndValidBinding()throws Exception{
  actor();for(String site:List.of("same-site","cross-site"))mvc.perform(put(base+"/payroll-reconciliation").header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put(base+"/payroll-reconciliation").contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(comparison);
  mvc.perform(get(base+"/payroll-report?month=2026-10&companyIds=bad")).andExpect(status().isBadRequest());
  when(comparison.compare(any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_BLOCKED",409));mvc.perform(put(base+"/payroll-reconciliation").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict());
 }
}
