package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceBulkScheduleController;
import com.rpb.reservation.workforce.scheduling.bulk.*;
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
@WebMvcTest(WorkforceBulkScheduleController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceBulkLocalRuntimeSecurityTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean BulkSchedulePlanService plans;@MockBean BulkScheduleService service;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 @Test void exactReadAndWritePathsReachScopedApplicationButOtherMethodsRemainDenied()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
  mvc.perform(get(base+"/schedule-board?from=2026-10-26")).andExpect(status().isOk());verify(plans).board(any(),any(),any(),isNull(),eq(""),eq(50),eq(0));
  mvc.perform(get(base+"/schedule-bulk/options")).andExpect(status().isOk());verify(plans).options(any(),any(),isNull(),eq(""),eq(50),eq(0));
  var shift=UUID.randomUUID();String detail=base+"/schedule-staffing/employees?from=2026-10-29&to=2026-10-29&shiftId="+shift;mvc.perform(get(detail)).andExpect(status().isOk());verify(plans).staffingEmployees(any(),any(),any(),any(),eq(shift),isNull(),eq(""),eq(50),eq(0));mvc.perform(put(base+"/schedule-staffing/employees").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put(base+"/schedule-bulk/preview").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(plans).preview(any(),any(),any());
  mvc.perform(put(base+"/schedule-bulk/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(service).commit(any(),any(),any(),any());
  mvc.perform(delete(base+"/schedule-bulk/"+UUID.randomUUID())).andExpect(status().isForbidden());mvc.perform(post(base+"/schedule-board")).andExpect(status().isForbidden());
 }
}
