package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.WorkforceAnnualWageController;
import com.rpb.reservation.workforce.statutory.annual.*;
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

@WebMvcTest(WorkforceAnnualWageController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceAnnualWageApiTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean AnnualWageService service;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce/statutory-year-contexts/"+UUID.randomUUID()+"/"+UUID.randomUUID()+"/2026";
 @Test void anonymousCrossSiteAndUndocumentedMethodsStayClosed()throws Exception{mvc.perform(get(base)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));mvc.perform(put(base).contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(post(base+"/reconciliations").header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(delete(base)).andExpect(status().isForbidden());mvc.perform(get(base+"/private")).andExpect(status().isForbidden());verifyNoInteractions(service);}
 @Test void preciseAuthenticatedRoutesReachServiceAndMapStale()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));mvc.perform(get(base)).andExpect(status().isOk());mvc.perform(put(base).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());mvc.perform(post(base+"/reconciliations").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(service).save(any(),any(),any(),any(),eq(2026),any());verify(service).reconcile(any(),any(),any(),any(),eq(2026),any());when(service.read(any(),any(),any(),any(),anyInt())).thenThrow(new com.rpb.reservation.workforce.domain.WorkforceException("AW_CONTEXT_STALE",409));mvc.perform(get(base)).andExpect(status().isConflict());}
}
