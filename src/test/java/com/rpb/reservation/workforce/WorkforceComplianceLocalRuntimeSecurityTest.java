package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.api.*;
import com.rpb.reservation.workforce.compliance.ComplianceService;
import com.rpb.reservation.workforce.leave.account.*;
import com.rpb.reservation.workforce.leave.encashment.LeaveEncashmentService;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({WorkforceComplianceController.class,WorkforceLeaveAccountController.class,WorkforceLeaveEncashmentController.class})
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local") @TestPropertySource(properties="rpb.local-auth.enabled=true")
class WorkforceComplianceLocalRuntimeSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean CurrentActorProvider actors;
    @MockBean ComplianceService policies;
    @MockBean LeaveAccountService accounts;
    @MockBean LeaveYearEndService years;
    @MockBean LeaveEncashmentService encashments;
    @MockBean com.rpb.reservation.appgate.application.AppGateService gate;
    @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService denialAudit;
    @Test void exactNewRoutesReachScopedServicesButOtherMethodsRemainDenied() throws Exception {
        when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),"staff",Set.of(),Set.of(),Set.of())));
        String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce",employee=UUID.randomUUID().toString();
        mvc.perform(get(base+"/compliance-reference")).andExpect(status().isOk());verify(policies).reference(any(),any());
        mvc.perform(get(base+"/employees/"+employee+"/employment-contracts")).andExpect(status().isOk());verify(policies).contracts(any(),any(),any());
        mvc.perform(get(base+"/leave-accounts?employeeId="+employee+"&year=2026")).andExpect(status().isOk());verify(accounts).page(any(),any(),any(),eq(2026));
        mvc.perform(post(base+"/leave-encashments/preview").header("X-RPB-Workforce","1").contentType("application/json").content("{}")).andExpect(status().isOk());verify(encashments).preview(any(),any(),any());
        mvc.perform(delete(base+"/compliance-policies/"+UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(post(base+"/leave-accounts/enable/"+UUID.randomUUID())).andExpect(status().isForbidden());
    }
    @Test void identityAndSameOriginWriteProtectionRemainRequired() throws Exception {
        when(actors.currentActor()).thenReturn(Optional.empty());String base="/api/v1/stores/"+UUID.randomUUID()+"/workforce";
        mvc.perform(get(base+"/compliance-policies")).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
        mvc.perform(post(base+"/leave-encashments/preview").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put(base+"/leave-encashments/"+UUID.randomUUID()).header("X-RPB-Workforce","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(encashments);
    }
}
