package com.rpb.reservation.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.platform.application.PlatformOperatingEntity;
import com.rpb.reservation.platform.application.PlatformStore;
import com.rpb.reservation.platform.application.PlatformTenantAdminStoreAccess;
import com.rpb.reservation.platform.application.PlatformTenantListResult;
import com.rpb.reservation.platform.application.PlatformTenantPage;
import com.rpb.reservation.platform.application.PlatformTenant;
import com.rpb.reservation.platform.application.PlatformTenantService;
import com.rpb.reservation.platform.application.PlatformTenantStructureService;
import com.rpb.reservation.platform.application.PlatformTenantStoreOption;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

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
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000983");
    private static final UUID OPERATING_ENTITY_ID = UUID.fromString("50000000-0000-0000-0000-000000000983");
    private static final UUID LOGO_MEDIA_ASSET_ID = UUID.fromString("91000000-0000-0000-0000-000000000983");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformTenantService tenantService;

    @MockBean
    private PlatformTenantStructureService structureService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void localRuntimeAllowsPlatformTenantApiWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(tenantService.listTenants(any())).thenReturn(new PlatformTenantListResult(
            List.of(tenant(null)),
            new PlatformTenantPage(20, 0, 1)
        ));

        mockMvc.perform(get("/api/v1/platform/tenants").param("includeDeleted", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenants[0].tenantCode").value("20000000"));
    }

    @Test
    void localRuntimeAllowsTenantAdminStoreAccessReadWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(tenantService.getTenantAdminStoreAccess(TENANT_ID)).thenReturn(new PlatformTenantAdminStoreAccess(
            List.of(new PlatformTenantStoreOption(
                STORE_ID,
                OPERATING_ENTITY_ID,
                "Local Operating Entity",
                "local-validation-store",
                "Local Validation Store",
                "active",
                "zh-CN",
                true
            )),
            List.of(STORE_ID),
            STORE_ID
        ));

        mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/admin-store-access", TENANT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.stores[0].storeId").value(STORE_ID.toString()))
            .andExpect(jsonPath("$.stores[0].operatingEntityId").value(OPERATING_ENTITY_ID.toString()))
            .andExpect(jsonPath("$.stores[0].operatingEntityName").value("Local Operating Entity"))
            .andExpect(jsonPath("$.storeIds[0]").value(STORE_ID.toString()))
            .andExpect(jsonPath("$.defaultStoreId").value(STORE_ID.toString()));
    }

    @Test
    void localRuntimeAllowsTenantStructureApisWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(structureService.listOperatingEntities(TENANT_ID)).thenReturn(List.of(operatingEntity()));
        when(structureService.listStores(TENANT_ID)).thenReturn(List.of(store()));

        mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/operating-entities", TENANT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.operatingEntities[0].entityCode").value("local-entity"));

        mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/stores", TENANT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.stores[0].storeCode").value("local-validation-store"))
            .andExpect(jsonPath("$.stores[0].operatingEntityName").value("Local Operating Entity"));
    }

    @Test
    void localRuntimeAllowsTenantLogoUploadWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(tenantService.uploadTenantLogo(eq(TENANT_ID), any(MultipartFile.class), any()))
            .thenReturn(tenant(LOGO_MEDIA_ASSET_ID));

        mockMvc.perform(multipart("/api/v1/platform/tenants/{tenantId}/logo", TENANT_ID)
                .file("file", new byte[] {
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                    0x00, 0x00, 0x00, 0x0d
                }))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.logoMediaUrl").value(
                "/api/v1/platform/tenants/" + TENANT_ID + "/logo/media/" + LOGO_MEDIA_ASSET_ID
            ));
    }

    @Test
    void localRuntimeAllowsTenantLogoClearWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(tenantService.clearTenantLogo(eq(TENANT_ID), any())).thenReturn(tenant(null));

        mockMvc.perform(delete("/api/v1/platform/tenants/{tenantId}/logo", TENANT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tenant.logoMediaUrl").doesNotExist());
    }

    @Test
    void localRuntimeAllowsTenantLogoMediaReadWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(tenantService.readTenantLogoMedia(TENANT_ID, LOGO_MEDIA_ASSET_ID))
            .thenReturn(new CallScreenMediaContent(new ByteArrayResource(new byte[] {1, 2, 3}), "image/png", 3));

        mockMvc.perform(get(
                "/api/v1/platform/tenants/{tenantId}/logo/media/{assetId}",
                TENANT_ID,
                LOGO_MEDIA_ASSET_ID
            ))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
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

    private static PlatformTenant tenant(UUID logoMediaAssetId) {
        return new PlatformTenant(
            TENANT_ID,
            "20000000",
            "食刻租户",
            "active",
            "zh-CN",
            "021-393930",
            "上海市徐汇区示例路 1 号",
            "张店长",
            logoMediaAssetId,
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            null
        );
    }

    private static PlatformOperatingEntity operatingEntity() {
        return new PlatformOperatingEntity(
            OPERATING_ENTITY_ID,
            TENANT_ID,
            "local-entity",
            "Local Operating Entity",
            "active",
            "zh-CN",
            "+6590000000",
            "1 Local Street",
            "Local Manager",
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            null
        );
    }

    private static PlatformStore store() {
        return new PlatformStore(
            STORE_ID,
            TENANT_ID,
            OPERATING_ENTITY_ID,
            "local-entity",
            "Local Operating Entity",
            "local-validation-store",
            "Local Validation Store",
            "active",
            "Asia/Singapore",
            "zh-CN",
            "DD-MM-YYYY",
            "HH:mm",
            "SGD",
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            null
        );
    }
}
