package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.*;
import com.rpb.reservation.workforce.payroll.governance.*;
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

class WorkforcePayBulkUpgradeApiTest {
 CurrentActorProvider actors=mock(CurrentActorProvider.class);PayChangeBulkService bulk=mock(PayChangeBulkService.class);TemplateUpgradeService upgrades=mock(TemplateUpgradeService.class);
 UUID store=UUID.randomUUID(),id=UUID.randomUUID(),company=UUID.randomUUID();String base="/api/v1/stores/"+store+"/workforce";
 MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforcePayBulkUpgradeController(actors,bulk,upgrades)).build();
 @Test void allNewRoutesKeepAuthenticationWriteOriginAndNoStore()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(base+"/pay-template-library/"+id+"/upgrade-preview").param("adoptionId",UUID.randomUUID().toString()).param("releaseVersion","2").param("effectiveDate","2026-11-01")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
  for(var path:List.of("/pay-change-bulk-actions","/pay-change-requests/"+id+"/upgrade")){
   mvc.perform(put(base+path).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   mvc.perform(put(base+path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
   mvc.perform(put(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  }verifyNoInteractions(bulk,upgrades);
 }
 @Test void partialResultsRemain200AndConflictMaps409()throws Exception{
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
  when(bulk.action(any(),eq(store),any())).thenReturn(new PayChangeBulkDtos.Response(List.of(new PayChangeBulkDtos.Result(id,UUID.randomUUID(),"failed",null,"WORKFORCE_CONFLICT",409))));
  mvc.perform(put(base+"/pay-change-bulk-actions").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].outcome").value("failed")).andExpect(jsonPath("$.results[0].httpStatus").value(409));
  when(upgrades.save(any(),any(),any(),any())).thenThrow(new WorkforceException("WORKFORCE_PAYROLL_UPGRADE_CONFLICT_UNRESOLVED",409));
  mvc.perform(put(base+"/pay-change-requests/"+id+"/upgrade").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_PAYROLL_UPGRADE_CONFLICT_UNRESOLVED"));
 }
}
