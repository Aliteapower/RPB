package com.rpb.reservation.queuedisplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import com.rpb.reservation.queuedisplay.application.PlatformCallScreenSeedService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class PlatformCallScreenMediaSeedControllerTest {
    private static final String MEDIA_ENDPOINT = "/api/v1/platform/call-screen/media";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000971");

    private CallScreenMediaService mediaService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PlatformCallScreenSeedService seedService = mock(PlatformCallScreenSeedService.class);
        mediaService = mock(CallScreenMediaService.class);
        MutableCurrentActorProvider actorProvider = new MutableCurrentActorProvider(
            actor(Set.of("platform_admin"), Set.of("platform.call_screen_ad.manage"))
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
            new PlatformCallScreenMediaSeedController(seedService, mediaService, actorProvider)
        ).build();
    }

    @Test
    void mapsOversizedPlatformMediaUploadToStableInvalidRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "intro.mp4",
            "video/mp4",
            new byte[] {0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70}
        );
        when(mediaService.uploadPlatformMedia(any())).thenThrow(new MaxUploadSizeExceededException(80L * 1024L * 1024L));

        mockMvc.perform(multipart(MEDIA_ENDPOINT).file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions) {
        return CurrentActor.storeStaff(TENANT_ID, ACTOR_ID, "platform_admin", roles, permissions, Set.of());
    }

    private static final class MutableCurrentActorProvider implements CurrentActorProvider {
        private final CurrentActor actor;

        private MutableCurrentActorProvider(CurrentActor actor) {
            this.actor = actor;
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.of(actor);
        }
    }
}
