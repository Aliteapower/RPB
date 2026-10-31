package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.api.WorkforceController;
import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkforceApiTest {
    WorkforceHomeService home=mock(WorkforceHomeService.class);
    WorkforceEmployeeService employees=mock(WorkforceEmployeeService.class);
    CurrentActorProvider actors=mock(CurrentActorProvider.class);
    String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceController(actors,home,employees,mock(WorkforceCompanyService.class),mock(WorkforceAdminService.class))).build();
    @Test void unauthenticatedReadReturnsStableErrorAndNoStore()throws Exception {
        when(actors.currentActor()).thenReturn(Optional.empty());
        mvc.perform(get(base+"/home")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.code").value("WORKFORCE_UNAUTHENTICATED"));
    }
    @Test void writesRejectCrossOriginAndMissingCustomHeaderBeforeService()throws Exception {
        for(String site:List.of("cross-site","same-site"))mvc.perform(put(base+"/employees/"+UUID.randomUUID()).header("X-RPB-Workforce","1").header("Sec-Fetch-Site",site).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORKFORCE_FORBIDDEN"));
        mvc.perform(put(base+"/employees/"+UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(employees);
    }
    @Test void applicationVersionConflictIs409WithoutDatabaseDetails()throws Exception {
        var actor=new com.rpb.reservation.walkin.api.CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of());
        when(actors.currentActor()).thenReturn(Optional.of(actor));when(employees.save(any(),any(),any(),any())).thenThrow(WorkforceException.conflict());
        mvc.perform(put(base+"/employees/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFORCE_CONFLICT")).andExpect(header().string("Cache-Control","private, no-store"));
    }
}
