package com.rpb.reservation.platform.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.platform.application.PlatformProfile;
import com.rpb.reservation.platform.application.PlatformProfileMutationCommand;
import com.rpb.reservation.platform.application.PlatformProfileService;
import com.rpb.reservation.platform.application.PlatformSocialLink;
import com.rpb.reservation.platform.application.PlatformSocialLinkMutationCommand;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(PlatformProfileController.class)
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
class PlatformProfileLocalRuntimeSecurityTest {
    private static final UUID PROFILE_LOGO_ID = UUID.fromString("92000000-0000-0000-0000-000000000983");
    private static final UUID SOCIAL_LINK_ID = UUID.fromString("93000000-0000-0000-0000-000000000983");
    private static final UUID SOCIAL_LINK_LOGO_ID = UUID.fromString("94000000-0000-0000-0000-000000000983");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformProfileService profileService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void localRuntimeAllowsPlatformProfileReadWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(profileService.getProfile()).thenReturn(profile(null, List.of()));

        mockMvc.perform(get("/api/v1/platform/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.profile.platformName").value("RPB"));
    }

    @Test
    void localRuntimeAllowsPlatformProfileUpdateWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(profileService.updateProfile(any(PlatformProfileMutationCommand.class)))
            .thenReturn(profile(null, List.of()));

        mockMvc.perform(patch("/api/v1/platform/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "platformName": "RPB",
                      "uen": "202600001A",
                      "address": "Shanghai",
                      "phone": "021-393930",
                      "email": "ops@example.com",
                      "website": "https://example.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void localRuntimeAllowsPlatformProfileLogoUploadWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(profileService.uploadProfileLogo(any(MultipartFile.class))).thenReturn(profile(PROFILE_LOGO_ID, List.of()));

        mockMvc.perform(multipart("/api/v1/platform/profile/logo")
                .file("file", pngHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.profile.logoMediaUrl").value(
                "/api/v1/platform/call-screen/media/" + PROFILE_LOGO_ID
            ));
    }

    @Test
    void localRuntimeAllowsPlatformProfileLogoClearWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(profileService.clearProfileLogo()).thenReturn(profile(null, List.of()));

        mockMvc.perform(delete("/api/v1/platform/profile/logo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.profile.logoMediaUrl").doesNotExist());
    }

    @Test
    void localRuntimeAllowsSocialLinkCreateUpdateDeleteWhenConfiguredActorIsPlatformAdmin() throws Exception {
        PlatformProfile withLink = profile(null, List.of(socialLink(null)));
        when(profileService.createSocialLink(any(PlatformSocialLinkMutationCommand.class))).thenReturn(withLink);
        when(profileService.updateSocialLink(eq(SOCIAL_LINK_ID), any(PlatformSocialLinkMutationCommand.class))).thenReturn(withLink);
        when(profileService.deleteSocialLink(SOCIAL_LINK_ID)).thenReturn(profile(null, List.of()));

        mockMvc.perform(post("/api/v1/platform/profile/social-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "Website",
                      "url": "https://example.com",
                      "sortOrder": 1,
                      "status": "active"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(patch("/api/v1/platform/profile/social-links/{linkId}", SOCIAL_LINK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "Website",
                      "url": "https://example.com",
                      "sortOrder": 1,
                      "status": "active"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/v1/platform/profile/social-links/{linkId}", SOCIAL_LINK_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void localRuntimeAllowsSocialLinkLogoUploadAndClearWhenConfiguredActorIsPlatformAdmin() throws Exception {
        when(profileService.uploadSocialLinkLogo(eq(SOCIAL_LINK_ID), any(MultipartFile.class)))
            .thenReturn(profile(null, List.of(socialLink(SOCIAL_LINK_LOGO_ID))));
        when(profileService.clearSocialLinkLogo(SOCIAL_LINK_ID))
            .thenReturn(profile(null, List.of(socialLink(null))));

        mockMvc.perform(multipart("/api/v1/platform/profile/social-links/{linkId}/logo", SOCIAL_LINK_ID)
                .file("file", pngHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.profile.socialLinks[0].logoMediaUrl").value(
                "/api/v1/platform/call-screen/media/" + SOCIAL_LINK_LOGO_ID
            ));

        mockMvc.perform(delete("/api/v1/platform/profile/social-links/{linkId}/logo", SOCIAL_LINK_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.profile.socialLinks[0].logoMediaUrl").doesNotExist());
    }

    @Test
    void localRuntimeRejectsPlatformProfileApiWhenActorIsStoreStaff() throws Exception {
        mockMvc.perform(get("/api/v1/platform/profile")
                .header("X-Test-Actor-Type", "staff")
                .header("X-Test-Actor-Role", "store_staff"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private static PlatformProfile profile(UUID logoMediaAssetId, List<PlatformSocialLink> socialLinks) {
        return new PlatformProfile(
            "RPB",
            "202600001A",
            "Shanghai",
            "021-393930",
            "ops@example.com",
            "https://example.com",
            logoMediaAssetId,
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            0,
            socialLinks
        );
    }

    private static PlatformSocialLink socialLink(UUID logoMediaAssetId) {
        return new PlatformSocialLink(
            SOCIAL_LINK_ID,
            "Website",
            "https://example.com",
            logoMediaAssetId,
            1,
            "active",
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            OffsetDateTime.parse("2026-06-25T00:00:00Z"),
            0
        );
    }

    private static byte[] pngHeader() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d
        };
    }
}
