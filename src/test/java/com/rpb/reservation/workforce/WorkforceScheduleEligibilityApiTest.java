package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.api.WorkforceAttendanceController;
import com.rpb.reservation.workforce.scheduling.*;
import com.rpb.reservation.workforce.attendance.*;
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

class WorkforceScheduleEligibilityApiTest {
    CurrentActorProvider actors=mock(CurrentActorProvider.class);
    ScheduleService schedules=mock(ScheduleService.class);
    String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/schedule";
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new WorkforceAttendanceController(actors,mock(ShiftService.class),schedules,mock(ClockService.class),mock(AttendanceQueryService.class),mock(CorrectionService.class))).build();
    @Test void candidatesRequireAuthenticationAndMustNotBeCached()throws Exception{
        when(actors.currentActor()).thenReturn(Optional.empty());
        mvc.perform(get(base+"?from=2026-10-30&to=2026-10-30")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
        verifyNoInteractions(schedules);
    }
    @Test void preciseRejectionsRetain400CodeAndNoStoreContract()throws Exception{
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"admin",Set.of("tenant_admin"),Set.of(),Set.of())));
        for(var code:List.of("WORKFORCE_SCHEDULE_DATE_OUT_OF_RANGE","WORKFORCE_SCHEDULE_BEFORE_HIRE","WORKFORCE_SCHEDULE_EMPLOYEE_LEFT","WORKFORCE_SCHEDULE_EMPLOYEE_INACTIVE","WORKFORCE_SCHEDULE_STORE_ASSIGNMENT","WORKFORCE_SCHEDULE_SHIFT_DISABLED")){
            doThrow(new WorkforceException(code,400)).when(schedules).save(any(),any(),any(),any());
            mvc.perform(put(base+"/"+UUID.randomUUID()).header("X-RPB-Workforce","1").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code)).andExpect(header().string("Cache-Control","private, no-store"));
        }
    }
    @Test void crossSiteOrMissingWriteHeaderCannotReachScheduleService()throws Exception{
        mvc.perform(put(base+"/"+UUID.randomUUID()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put(base+"/"+UUID.randomUUID()).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(schedules);
    }
}
