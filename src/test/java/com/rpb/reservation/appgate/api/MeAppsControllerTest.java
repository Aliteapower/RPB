package com.rpb.reservation.appgate.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.application.AppGateAppEntry;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MeAppsControllerTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000008301");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000008301");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000008301");

    private AppGateService appGateService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        appGateService = mock(AppGateService.class);
        actorProvider = new MutableCurrentActorProvider(actor());
        mockMvc = MockMvcBuilders
            .standaloneSetup(new MeAppsController(appGateService, actorProvider))
            .build();
    }

    @Test
    void returnsVisibleReservationQueueAppForCurrentStore() throws Exception {
        when(appGateService.visibleApps(actorProvider.actor, STORE_ID)).thenReturn(List.of(
            new AppGateAppEntry(
                "reservation_queue",
                "订位排号系统",
                "active",
                "/stores/" + STORE_ID + "/staff",
                true,
                Set.of("reservation.create", "cleaning.complete")
            )
        ));

        mockMvc.perform(get("/api/me/apps").param("storeId", STORE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.apps[0].appKey").value("reservation_queue"))
            .andExpect(jsonPath("$.apps[0].appName").value("订位排号系统"))
            .andExpect(jsonPath("$.apps[0].status").value("active"))
            .andExpect(jsonPath("$.apps[0].entryRoute").value("/stores/" + STORE_ID + "/staff"))
            .andExpect(jsonPath("$.apps[0].entryVisible").value(true))
            .andExpect(jsonPath("$.apps[0].permissions").isArray());
    }

    @Test
    void returnsEmptyAppsWhenNoCurrentActor() throws Exception {
        actorProvider.actor = null;

        mockMvc.perform(get("/api/me/apps").param("storeId", STORE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.apps").isEmpty());

        verifyNoInteractions(appGateService);
    }

    @Test
    void returnsEmptyAppsWhenServiceFindsNoVisibleAvailableApps() throws Exception {
        when(appGateService.visibleApps(actorProvider.actor, STORE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/me/apps").param("storeId", STORE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.apps").isEmpty());
    }

    private static CurrentActor actor() {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("reservation.create", "cleaning.complete"),
            Set.of(STORE_ID)
        );
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
