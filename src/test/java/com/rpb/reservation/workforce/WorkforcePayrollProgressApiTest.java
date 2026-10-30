package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.api.WorkforcePayrollProgressController;
import com.rpb.reservation.workforce.payroll.*;
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

class WorkforcePayrollProgressApiTest {
    CurrentActorProvider actors=mock(CurrentActorProvider.class);PayrollProgressService progress=mock(PayrollProgressService.class);PayrollBulkService bulk=mock(PayrollBulkService.class);
    String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforcePayrollProgressController(actors,progress,bulk)).build();
    @Test void readsRequireActorAndArePrivate()throws Exception{
        when(actors.currentActor()).thenReturn(Optional.empty());
        mvc.perform(get(base+"/payroll-batches/"+UUID.randomUUID()+"/changes")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
    }
    @Test void bulkRejectsCrossSiteAndMissingHeaderBeforeService()throws Exception{
        String path=base+"/payroll-batch-operations/"+UUID.randomUUID();
        mvc.perform(put(path).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        for(String site:List.of("same-site","cross-site"))mvc.perform(put(path).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(bulk);
    }
    @Test void historyPermissionAndPayloadConflictsUseStableErrors()throws Exception{
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
        when(progress.changes(any(),any(),any())).thenThrow(WorkforceException.forbidden());
        mvc.perform(get(base+"/payroll-batches/"+UUID.randomUUID()+"/changes")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKFORCE_FORBIDDEN"));
        when(bulk.execute(any(),any(),any(),any())).thenThrow(WorkforceException.conflict());
        mvc.perform(put(base+"/payroll-batch-operations/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","private, no-store"));
    }
    @Test void partialFailureIsHttp200AndContainsNoFailedBatchSalary()throws Exception{
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
        when(bulk.execute(any(),any(),any(),any())).thenReturn(new PayrollProgressDtos.BulkResult(UUID.randomUUID(),"calculate",List.of(new PayrollProgressDtos.BulkItem(UUID.randomUUID(),"failed","WORKFORCE_FORBIDDEN",null))));
        mvc.perform(put(base+"/payroll-batch-operations/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("failed")).andExpect(jsonPath("$.results[0].batch").isEmpty());
    }
}
