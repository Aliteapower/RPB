package com.rpb.reservation.queuedisplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.queuedisplay.application.CallScreenAdSet;
import com.rpb.reservation.queuedisplay.application.CallScreenAdminService;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import com.rpb.reservation.queuedisplay.application.CallScreenSetting;
import com.rpb.reservation.queuedisplay.application.CallScreenTextSlide;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class CallScreenAdminControllerTest {
    private static final String SETTINGS_ENDPOINT = "/api/v1/stores/{storeId}/tenant-admin/call-screen/settings";
    private static final String AD_SET_ENDPOINT = "/api/v1/stores/{storeId}/tenant-admin/call-screen/ad-sets/{adSetId}";
    private static final String MEDIA_ENDPOINT = "/api/v1/stores/{storeId}/tenant-admin/call-screen/media";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000975");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000975");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000975");
    private static final UUID AD_SET_ID = UUID.fromString("81000000-0000-0000-0000-000000000975");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CallScreenAdminService service;
    private CallScreenMediaService mediaService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CallScreenAdminService.class);
        mediaService = mock(CallScreenMediaService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("tenant_admin"), Set.of("tenant.admin.manage"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders.standaloneSetup(new CallScreenAdminController(service, mediaService, actorProvider)).build();
    }

    @Test
    void mapsGetAndPatchSettings() throws Exception {
        when(service.getSettings(any())).thenReturn(setting());
        when(service.updateSettings(any(), any())).thenReturn(setting());

        mockMvc.perform(get(SETTINGS_ENDPOINT, STORE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.settings.activeAdSetId").value(AD_SET_ID.toString()))
            .andExpect(jsonPath("$.settings.statePollSeconds").value(3));

        mockMvc.perform(patch(SETTINGS_ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new CallScreenAdminRequests.SettingsRequest(
                    AD_SET_ID,
                    "text",
                    "active",
                    6,
                    4,
                    true,
                    0
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(service).getSettings(any());
        verify(service).updateSettings(any(), any());
    }

    @Test
    void mapsGetAndPatchAdSet() throws Exception {
        when(service.getAdSet(any(), eq(AD_SET_ID))).thenReturn(adSet());
        when(service.updateAdSet(any(), eq(AD_SET_ID), any())).thenReturn(adSet());

        mockMvc.perform(get(AD_SET_ENDPOINT, STORE_ID, AD_SET_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.adSet.id").value(AD_SET_ID.toString()))
            .andExpect(jsonPath("$.adSet.slides[0].title").value("欢迎光临"));

        mockMvc.perform(patch(AD_SET_ENDPOINT, STORE_ID, AD_SET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new CallScreenAdminRequests.AdSetRequest(
                    "默认文案",
                    "text",
                    "active",
                    List.of(new CallScreenAdminRequests.TextSlideRequest(
                        null,
                        "欢迎光临",
                        "食刻",
                        "标语",
                        1,
                        "active",
                        null
                    )),
                    0
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(service).getAdSet(any(), eq(AD_SET_ID));
        verify(service).updateAdSet(any(), eq(AD_SET_ID), any());
    }

    @Test
    void permissionScopeAndPersistenceErrorsMapToStableCodes() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("tenant.admin.manage"), Set.of(STORE_ID));
        mockMvc.perform(get(SETTINGS_ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = actor(Set.of("tenant_admin"), Set.of(), Set.of(STORE_ID));
        mockMvc.perform(get(SETTINGS_ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = actor(Set.of("tenant_admin"), Set.of("tenant.admin.manage"), Set.of());
        mockMvc.perform(get(SETTINGS_ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(service);

        actorProvider.actor = actor(Set.of("tenant_admin"), Set.of("tenant.admin.manage"), Set.of(STORE_ID));
        when(service.getSettings(any())).thenThrow(new DataAccessResourceFailureException("db_down"));
        mockMvc.perform(get(SETTINGS_ENDPOINT, STORE_ID))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("PERSISTENCE_ERROR"));
    }

    @Test
    void mapsOversizedTenantMediaUploadToStableInvalidRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "intro.mp4",
            "video/mp4",
            new byte[] {0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70}
        );
        when(mediaService.uploadTenantMedia(any(), any())).thenThrow(new MaxUploadSizeExceededException(80L * 1024L * 1024L));

        mockMvc.perform(multipart(MEDIA_ENDPOINT, STORE_ID).file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    private static CallScreenSetting setting() {
        return new CallScreenSetting(AD_SET_ID, "text", "active", 5, 3, true, 0);
    }

    private static CallScreenAdSet adSet() {
        return new CallScreenAdSet(
            AD_SET_ID,
            "默认文案",
            "text",
            "active",
            List.of(new CallScreenTextSlide(null, "欢迎光临", "食刻", "标语", 1, "active", 0)),
            0
        );
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(TENANT_ID, ACTOR_ID, "staff", roles, permissions, storeIds);
    }

    private static final class MutableCurrentActorProvider implements CurrentActorProvider {
        private CurrentActor actor;

        private MutableCurrentActorProvider(CurrentActor actor) {
            this.actor = actor;
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(actor);
        }
    }
}
