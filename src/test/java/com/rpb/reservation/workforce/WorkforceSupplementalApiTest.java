package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.api.*;
import com.rpb.reservation.workforce.payroll.supplemental.*;
import com.rpb.reservation.workforce.statutory.settlement.*;
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

@WebMvcTest({WorkforceSupplementalController.class,WorkforceStatutorySettlementController.class})
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceSupplementalApiTest {
 @Autowired MockMvc mvc;@MockBean CurrentActorProvider actors;@MockBean SupplementalService supplemental;@MockBean SettlementService settlement;
 @MockBean com.rpb.reservation.appgate.application.AppGateService gate;@MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
 final UUID company=UUID.randomUUID(),employee=UUID.randomUUID(),id=UUID.randomUUID();final String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
 @Test void anonymousCrossSiteWrongMethodsAndNestedPathsStayClosed()throws Exception{for(var path:List.of("/supplemental-month-summary?companyId="+company+"&month=2026-10","/supplemental-batches/"+id,"/statutory-remittances/"+id,"/my-supplemental-payslips/"+id,"/aw-reconciliations/"+id)){mvc.perform(get(base+path)).andExpect(status().isUnauthorized());mvc.perform(delete(base+path)).andExpect(status().isForbidden());mvc.perform(get(base+path.split("\\?",2)[0]+"/private")).andExpect(status().isForbidden());}mvc.perform(post(base+"/supplemental-batches/"+id+"/actions").header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());mvc.perform(put(base+"/statutory-remittances/"+id).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(supplemental,settlement);}
 @Test void originalPeriodSummaryIsAnExactPrivateRead()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));mvc.perform(get(base+"/supplemental-month-summary").param("companyId",company.toString()).param("month","2026-10")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"));verify(supplemental).monthSummary(any(),any(),eq(company),eq("2026-10"));mvc.perform(put(base+"/supplemental-month-summary")).andExpect(status().isForbidden());}
 @Test void exactReadAndCommandRoutesReachApplicationServices()throws Exception{when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));for(var path:List.of("/supplemental-batches/"+id,"/statutory-remittances/"+id,"/my-supplemental-payslips/"+id,"/aw-reconciliations/"+id,"/statutory-settlement-contexts/"+company+"/"+employee+"/2026-10","/statutory-company-months/"+company+"/2026-10"))mvc.perform(get(base+path)).andExpect(status().isOk());for(var path:List.of("/supplemental-batches/"+id,"/supplemental-batches/"+id+"/payments/"+UUID.randomUUID(),"/statutory-remittances/"+id,"/statutory-settlement-contexts/"+company+"/"+employee+"/2026-10","/statutory-company-months/"+company+"/2026-10"))mvc.perform(put(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());for(var path:List.of("/supplemental-batches/"+id+"/actions","/aw-reconciliations/"+id+"/settlements","/statutory-settlement-contexts/"+company+"/"+employee+"/2026-10/settlements"))mvc.perform(post(base+path).header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(supplemental).payment(any(),any(),eq(id),any(),any());verify(settlement).settleMonth(any(),any(),eq(company),eq(employee),eq("2026-10"),any());when(supplemental.detail(any(),any(),eq(id))).thenThrow(new com.rpb.reservation.workforce.domain.WorkforceException("SUPPLEMENTAL_SOURCE_CHANGED",409));mvc.perform(get(base+"/supplemental-batches/"+id)).andExpect(status().isConflict());}
}
