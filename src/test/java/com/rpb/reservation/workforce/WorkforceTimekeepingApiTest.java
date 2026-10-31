package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceTimekeepingController;
import com.rpb.reservation.workforce.approval.*;
import com.rpb.reservation.workforce.leave.*;
import com.rpb.reservation.workforce.calendar.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class WorkforceTimekeepingApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);LeaveService leaves=mock(LeaveService.class);ApprovalService approvals=mock(ApprovalService.class);
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceTimekeepingController(actors,mock(LeaveTypeService.class),leaves,mock(CalendarService.class),approvals,mock(WorkSummaryService.class))).build();
 @Test void everyNewReadRequiresIdentityAndPreventsCaching()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.empty());for(String path:List.of("/leave-types","/leaves?from=2026-10-01&to=2026-10-31","/calendar?from=2026-10-01&to=2026-10-31","/approvals?from=2026-10-01&to=2026-10-31","/approvals/"+UUID.randomUUID(),"/work-summary?from=2026-10-01&to=2026-10-31"))mvc.perform(get(base+path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
 }
 @Test void writesRejectCrossSiteOrAbsentCustomHeaderBeforeService()throws Exception{
  for(String path:List.of("/leave-types/","/leaves/","/calendar/","/approvals/")){for(String site:List.of("same-site","cross-site"))mvc.perform(put(base+path+UUID.randomUUID()).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(base+path+UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isForbidden());}verifyNoInteractions(leaves,approvals);
 }
 @Test void blockedApprovalReturnsStable409AndBadDate400()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));when(approvals.approve(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_APPROVAL_BLOCKED",409));
  mvc.perform(put(base+"/approvals/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_APPROVAL_BLOCKED"));
  mvc.perform(get(base+"/leaves?from=bad&to=2026-10-31")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WORKFORCE_INVALID"));
 }
}
