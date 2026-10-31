package com.rpb.reservation.dailyledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.appgate.guard.AppGateInterceptor;
import com.rpb.reservation.dailyledger.api.*;
import com.rpb.reservation.dailyledger.application.*;
import com.rpb.reservation.dailyledger.domain.LedgerRules;
import com.rpb.reservation.dailyledger.persistence.LedgerRepository;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerApiTest {
    UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),actorId=UUID.randomUUID();
    ObjectMapper json=new ObjectMapper();
    CurrentActor actor=new CurrentActor(tenant,actorId,"staff",Set.of("store_staff"),Set.of("daily_ledger.view"),Set.of(store));
    @Test void appGateDenialsDoNotInvokeLedgerQueries() throws Exception {
        for(var reason:List.of(AppGateDenyReason.APP_DISABLED,AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.STORE_APP_NOT_ENABLED,AppGateDenyReason.PERMISSION_DENIED)){
            var queries=mock(LedgerQueryService.class);var gate=mock(AppGateService.class);
            when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.deny("daily_ledger",tenant,store,"daily_ledger.view",reason));
            var provider=(CurrentActorProvider)()->Optional.of(actor);
            var mvc=MockMvcBuilders.standaloneSetup(new LedgerController(queries,mock(LedgerCommandService.class),provider,json,mock(LedgerBrandingService.class)))
                .addInterceptors(new AppGateInterceptor(gate,mock(AppGateDenialAuditService.class),provider,new AppGateApiErrorMapper(),json)).build();
            mvc.perform(get("/api/v1/stores/"+store+"/daily-ledger/settings")).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/stores/"+store+"/daily-ledger/branding/logo/"+UUID.randomUUID())).andExpect(status().isForbidden());
            verifyNoInteractions(queries);
        }
    }
    @Test void staffLogoReadUsesLedgerViewAndPrivateMediaResponse() throws Exception {
        var queries=mock(LedgerQueryService.class);var gate=mock(AppGateService.class);var branding=mock(LedgerBrandingService.class);
        when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.allow("daily_ledger",tenant,store,"daily_ledger.view"));
        UUID asset=UUID.randomUUID();
        when(branding.logo(any(),eq(asset))).thenReturn(new com.rpb.reservation.queuedisplay.application.CallScreenMediaContent(
            new org.springframework.core.io.ByteArrayResource(new byte[]{1,2,3}),"image/png",3));
        var provider=(CurrentActorProvider)()->Optional.of(actor);
        var mvc=MockMvcBuilders.standaloneSetup(new LedgerController(queries,mock(LedgerCommandService.class),provider,json,branding))
            .setControllerAdvice(new LedgerErrorHandler())
            .addInterceptors(new AppGateInterceptor(gate,mock(AppGateDenialAuditService.class),provider,new AppGateApiErrorMapper(),json)).build();
        mvc.perform(get("/api/v1/stores/"+store+"/daily-ledger/branding/logo/"+asset)).andExpect(status().isOk())
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(new byte[]{1,2,3}))
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("private")));
        verify(queries).display(any(),eq(actor));
    }
    @Test void settingsResponseHasExplicitEnvelopeAndMutationUsesManagePermission() throws Exception {
        var queries=mock(LedgerQueryService.class);var gate=mock(AppGateService.class);var commands=mock(LedgerCommandService.class);
        when(gate.evaluateHistory(any())).thenReturn(AppGateDecision.allow("daily_ledger",tenant,store,"daily_ledger.view"));
        when(queries.settings(any(),any())).thenReturn(new LedgerRepository.Settings(new LedgerRules(json).defaults(),json.createObjectNode(),2));
        var provider=(CurrentActorProvider)()->Optional.of(actor);
        var mvc=MockMvcBuilders.standaloneSetup(new LedgerController(queries,commands,provider,json,mock(LedgerBrandingService.class)))
            .setControllerAdvice(new LedgerErrorHandler())
            .addInterceptors(new AppGateInterceptor(gate,mock(AppGateDenialAuditService.class),provider,new AppGateApiErrorMapper(),json)).build();
        mvc.perform(get("/api/v1/stores/"+store+"/daily-ledger/settings")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.config.base_amount").value("500.00"));
        when(gate.evaluate(argThat(r->r.requiredPermission().equals("daily_ledger.settings.manage"))))
            .thenReturn(AppGateDecision.deny("daily_ledger",tenant,store,"daily_ledger.settings.manage",AppGateDenyReason.PERMISSION_DENIED));
        mvc.perform(put("/api/v1/stores/"+store+"/daily-ledger/settings").contentType("application/json").content("{\"version\":0,\"config\":{},\"branding\":{}}"))
            .andExpect(status().isForbidden());verifyNoInteractions(commands);
    }
}
