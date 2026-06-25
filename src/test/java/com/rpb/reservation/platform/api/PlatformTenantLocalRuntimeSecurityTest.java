package com.rpb.reservation.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.platform.application.PlatformTenantListResult;
import com.rpb.reservation.platform.application.PlatformTenantPage;
import com.rpb.reservation.platform.application.PlatformTenant;
import com.rpb.reservation.platform.application.PlatformTenantService;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.time.OffsetDateTime;
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

@WebMvcTest(PlatformTenantController.class)
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
    "rpb.local-auth.permissions[0]=platform.tenant.manage"
})
class PlatformTenantLocalRuntimeSecurityTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000983");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformTenantService tenantService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void localRuntimeAllowsPlatformTenantApiWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(tenantService.listTenants(any())).thenReturn(new PlatformTenantListResult(List.of(new PlatformTenant(
            TENANT_ID,
            "20000000",
            "食刻租户",
            "active",
            "zh-CN",
            "021-393930",
            "上海市徐汇区示例路 1 号",
            "张店长",
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            null
        )), new PlatformTenantPage(20, 0, 1)));

        mockMvc.perform(get("/api/v1/platform/tenants").param("includeDeleted", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenants[0].tenantCode").value("20000000"));
    }

    @Test
    void localRuntimeRejectsPlatformTenantApiWhenActorIsStoreStaff() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants")
                .header("X-Test-Actor-Type", "staff")
                .header("X-Test-Actor-Role", "store_staff")
                .param("includeDeleted", "true"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void localRuntimeRejectsPlatformTenantApiWhenPlatformAdminLacksTenantManagePermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants")
                .header("X-Test-Actor-Type", "platform_admin")
                .header("X-Test-Actor-Role", "platform_admin")
                .header("X-Test-Permissions", "reservation.today_view")
                .param("includeDeleted", "true"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
