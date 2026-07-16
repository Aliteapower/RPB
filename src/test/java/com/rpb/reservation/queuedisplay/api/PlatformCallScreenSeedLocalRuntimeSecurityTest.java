package com.rpb.reservation.queuedisplay.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedService;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSet;
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
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;

@WebMvcTest(PlatformCallScreenSeedController.class)
@Import({
    AppGateApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000983",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000901",
    "rpb.local-auth.actor-type=platform_admin",
    "rpb.local-auth.roles[0]=platform_admin",
    "rpb.local-auth.permissions[0]=platform.call_screen_ad.manage"
})
class PlatformCallScreenSeedLocalRuntimeSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformCallScreenSeedService service;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void localRuntimeAllowsPlatformTextSeedApiWhenConfiguredActorHasSeedManagePermission() throws Exception {
        when(service.getTextSeed()).thenReturn(new PlatformCallScreenSeedSet(
            UUID.fromString("82000000-0000-0000-0000-000000000983"),
            "restaurant_default",
            "餐厅默认叫号屏文案",
            "text",
            "active",
            List.of(),
            0
        ));

        mockMvc.perform(get("/api/v1/platform/call-screen/text-seed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.seedSet.seedKey").value("restaurant_default"));
    }

    @Test
    void localRuntimeAllowsExistingPlatformTenantManagerToOpenTextSeedApi() throws Exception {
        when(service.getTextSeed()).thenReturn(new PlatformCallScreenSeedSet(
            UUID.fromString("82000000-0000-0000-0000-000000000983"),
            "restaurant_default",
            "餐厅默认叫号屏文案",
            "text",
            "active",
            List.of(),
            0
        ));

        mockMvc.perform(get("/api/v1/platform/call-screen/text-seed")
                .header("X-Test-Actor-Type", "platform_admin")
                .header("X-Test-Actor-Role", "platform_admin")
                .header("X-Test-Permissions", "platform.tenant.manage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void localRuntimeRejectsPlatformTextSeedApiWhenPlatformAdminLacksPlatformManagePermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/call-screen/text-seed")
                .header("X-Test-Actor-Type", "platform_admin")
                .header("X-Test-Actor-Role", "platform_admin")
                .header("X-Test-Permissions", "queue.display.view"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
