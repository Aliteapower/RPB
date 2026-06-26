package com.rpb.reservation.queuedisplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedService;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedServiceErrorCode;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedServiceException;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSet;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedSlide;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformCallScreenSeedControllerTest {
    private static final String ENDPOINT = "/api/v1/platform/call-screen/text-seed";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000971");
    private static final UUID SEED_SET_ID = UUID.fromString("82000000-0000-0000-0000-000000000971");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlatformCallScreenSeedService service;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PlatformCallScreenSeedService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("platform_admin"), Set.of("platform.call_screen_ad.manage")));
        mockMvc = MockMvcBuilders.standaloneSetup(new PlatformCallScreenSeedController(service, actorProvider)).build();
    }

    @Test
    void mapsGetAndPatchTextSeed() throws Exception {
        when(service.getTextSeed()).thenReturn(seedSet());
        when(service.updateTextSeed(any())).thenReturn(seedSet());

        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.seedSet.seedKey").value("restaurant_default"))
            .andExpect(jsonPath("$.seedSet.adType").value("text"))
            .andExpect(jsonPath("$.seedSet.slides[0].title").value("欢迎光临"));

        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new PlatformCallScreenSeedRequests.TextSeedRequest(
                    "餐厅默认叫号屏文案",
                    "active",
                    List.of(new PlatformCallScreenSeedRequests.TextSeedSlideRequest(
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

        verify(service).getTextSeed();
        verify(service).updateTextSeed(any());
    }

    @Test
    void allowsPlatformTenantManagerForSeedTemplateCompatibility() throws Exception {
        actorProvider.actor = actor(Set.of("platform_admin"), Set.of("platform.tenant.manage"));
        when(service.getTextSeed()).thenReturn(seedSet());

        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(service).getTextSeed();
    }

    @Test
    void requiresPlatformAdminRoleAndPlatformManagePermission() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("platform.call_screen_ad.manage"));
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = actor(Set.of("platform_admin"), Set.of("queue.display.view"));
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = null;
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsPersistenceErrorsToStableCode() throws Exception {
        when(service.getTextSeed()).thenThrow(new DataAccessResourceFailureException("db_down"));

        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("PERSISTENCE_ERROR"));
    }

    @Test
    void mapsNullSlidePayloadToInvalidRequestInsteadOfServerError() throws Exception {
        when(service.updateTextSeed(any()))
            .thenThrow(new PlatformCallScreenSeedServiceException(PlatformCallScreenSeedServiceErrorCode.REQUEST_INVALID));

        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "餐厅默认叫号屏文案",
                      "status": "active",
                      "slides": [null],
                      "version": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    private static PlatformCallScreenSeedSet seedSet() {
        return new PlatformCallScreenSeedSet(
            SEED_SET_ID,
            "restaurant_default",
            "餐厅默认叫号屏文案",
            "text",
            "active",
            List.of(new PlatformCallScreenSeedSlide(null, "欢迎光临", "食刻", "标语", 1, "active", 0)),
            0
        );
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions) {
        return CurrentActor.storeStaff(TENANT_ID, ACTOR_ID, "platform_admin", roles, permissions, Set.of());
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
