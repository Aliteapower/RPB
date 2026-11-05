package com.rpb.reservation.platformbilling.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionHistoryService;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformSubscriptionHistoryController.class)
@Import({AppGateApiErrorMapper.class, LocalRuntimeCurrentActorProvider.class, LocalRuntimeSecurityConfiguration.class})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000983",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000901",
    "rpb.local-auth.actor-type=platform_admin",
    "rpb.local-auth.roles[0]=platform_admin",
    "rpb.local-auth.permissions[0]=platform.billing.manage"
})
class PlatformSubscriptionHistoryLocalRuntimeTest {
    @Autowired MockMvc mvc;
    @MockBean ProductSubscriptionHistoryService history;
    @MockBean AppGateService appGateService;
    @MockBean AppGateDenialAuditService appGateDenialAuditService;

    @Test void localSecurityAllowsOnlyTheAuthorizedPlatformHistoryRead() throws Exception {
        var tenant = UUID.randomUUID();
        var subscription = UUID.randomUUID();
        var path = "/api/v1/platform/tenants/" + tenant + "/product-subscriptions/" + subscription + "/events";
        when(history.list(tenant, subscription)).thenReturn(List.of());
        mvc.perform(get(path)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store")).andExpect(jsonPath("$.events").isEmpty());
        mvc.perform(get(path).header("X-Test-Permissions", "reservation.today_view"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
