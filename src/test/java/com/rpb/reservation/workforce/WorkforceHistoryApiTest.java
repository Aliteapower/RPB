package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceHistoryController;
import com.rpb.reservation.workforce.application.WorkforceHistoryService;
import com.rpb.reservation.workforce.attendance.AttendanceQueryService;
import com.rpb.reservation.workforce.attendance.daily.AttendanceDailyService;
import com.rpb.reservation.workforce.payroll.PayslipService;
import com.rpb.reservation.workforce.payroll.segment.SegmentService;
import com.rpb.reservation.workforce.payroll.supplemental.SupplementalService;
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
class WorkforceHistoryApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);WorkforceHistoryService history=mock(WorkforceHistoryService.class);AttendanceQueryService attendance=mock(AttendanceQueryService.class);AttendanceDailyService daily=mock(AttendanceDailyService.class);PayslipService slips=mock(PayslipService.class);SegmentService segments=mock(SegmentService.class);SupplementalService supplements=mock(SupplementalService.class);
 UUID store=UUID.randomUUID();String base="/api/v1/stores/"+store+"/workforce/history";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceHistoryController(actors,history,attendance,daily,slips,segments,supplements)).build();
 void login(){when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of(store))));}
 @Test void historyIsPrivateRequiresActorAndExposesNoWriteMethod()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(base+"/context")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  login();mvc.perform(put(base+"/context").header("X-RPB-Workforce","1")).andExpect(status().isMethodNotAllowed());verifyNoInteractions(history);
 }
 @Test void contextIsMinimalAndPermissionErrorsStayStable()throws Exception{
  login();when(history.context(any(),eq(store))).thenReturn(new WorkforceHistoryService.Context(false,true,List.of()));
  mvc.perform(get(base+"/context")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.self").value(true)).andExpect(jsonPath("$.accounts").doesNotExist());
  when(history.context(any(),eq(store))).thenThrow(WorkforceException.forbidden());mvc.perform(get(base+"/context")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKFORCE_FORBIDDEN"));
 }
 @Test void detailScopeMustBeExplicitAndValid()throws Exception{
  login();var id=UUID.randomUUID();mvc.perform(get(base+"/payslips/"+id+"?scope=all")).andExpect(status().isBadRequest());verifyNoInteractions(slips);
  mvc.perform(get(base+"/payslips/"+id)).andExpect(status().isOk());verify(slips).historyDetail(any(),eq(store),eq(id),eq(false),isNull());
 }
 @Test void settlementHistoryUsesValidatedExplicitSalaryScopeForListsAndDetails()throws Exception{
  login();var id=UUID.randomUUID();var company=UUID.randomUUID();
  mvc.perform(get(base+"/segments?year=2026&scope=all")).andExpect(status().isBadRequest());mvc.perform(get(base+"/supplements/"+id+"?scope=all")).andExpect(status().isBadRequest());verifyNoInteractions(segments,supplements);
  mvc.perform(get(base+"/segments?year=2026&scope=managed&companyId="+company)).andExpect(status().isOk());verify(segments).history(any(),eq(store),eq(2026),eq(true),eq(company));
  mvc.perform(get(base+"/segments/"+id+"?scope=managed")).andExpect(status().isOk());verify(segments).historyDetail(any(),eq(store),eq(id),eq(true));
  mvc.perform(get(base+"/supplements?year=2026&scope=managed&companyId="+company)).andExpect(status().isOk());verify(supplements).history(any(),eq(store),eq(2026),eq(true),eq(company));
  mvc.perform(get(base+"/supplements/"+id)).andExpect(status().isOk());verify(supplements).historyDetail(any(),eq(store),eq(id),eq(false));
 }
}
