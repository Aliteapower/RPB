package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceHistoryController;
import com.rpb.reservation.workforce.application.WorkforceHistoryService;
import com.rpb.reservation.workforce.attendance.AttendanceQueryService;
import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService;
import com.rpb.reservation.workforce.payroll.PayslipService;
import com.rpb.reservation.workforce.payroll.segment.SegmentService;
import com.rpb.reservation.workforce.payroll.supplemental.SupplementalService;
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
@WebMvcTest(WorkforceHistoryController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceHistoryLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean WorkforceHistoryService history;@MockBean AttendanceQueryService attendance;@MockBean AttendanceDailyService daily;@MockBean PayslipService slips;@MockBean SegmentService segments;@MockBean SupplementalService supplements;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void onlyExactHistoricalReadsCanReachControllers()throws Exception{
  UUID store=UUID.randomUUID(),id=UUID.randomUUID(),company=UUID.randomUUID();when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store))));String base="/api/v1/stores/"+store+"/workforce/history";
  for(var path:List.of("/context","/attendance?from=2026-10-01&to=2026-10-02","/attendance/"+id,"/attendance-month?month=2026-10","/payslips?year=2026","/payslips/"+id,"/batches?year=2026&companyId="+company,"/segments?year=2026","/segments/"+id,"/supplements?year=2026","/supplements/"+id))mvc.perform(get(base+path)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));
  verify(history).context(any(),eq(store));verify(daily).historyMonth(any(),eq(store),any(),eq(30),eq(0));verify(slips).historyBatches(any(),eq(store),eq(2026),eq(company));
  clearInvocations(history,attendance,daily,slips,segments,supplements);
  for(var path:List.of("/context","/payslips/"+id,"/attendance-month/refresh","/attendance/"+id+"/review","/segments/"+id,"/supplements/"+id))mvc.perform(put(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(get(base+"/unknown")).andExpect(status().isForbidden());mvc.perform(get(base+"/attendance/"+id+"/events")).andExpect(status().isForbidden());
  verifyNoInteractions(history,attendance,daily,slips,segments,supplements);
 }
}
