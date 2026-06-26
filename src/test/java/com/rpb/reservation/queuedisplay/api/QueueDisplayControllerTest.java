package com.rpb.reservation.queuedisplay.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queuedisplay.application.QueueDisplayAdSlide;
import com.rpb.reservation.queuedisplay.application.QueueDisplayAds;
import com.rpb.reservation.queuedisplay.application.QueueDisplayApplicationService;
import com.rpb.reservation.queuedisplay.application.QueueDisplayCurrentCall;
import com.rpb.reservation.queuedisplay.application.QueueDisplayQuery;
import com.rpb.reservation.queuedisplay.application.QueueDisplayResult;
import com.rpb.reservation.queuedisplay.application.QueueDisplayWaitingPreviewItem;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QueueDisplayControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-display/state";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000973");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000973");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000974");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000973");
    private static final UUID TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000973");

    private QueueDisplayApplicationService service;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(QueueDisplayApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("queue.display.view"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders.standaloneSetup(new QueueDisplayController(
            service,
            actorProvider,
            new QueueDisplayApiErrorMapper()
        )).build();
    }

    @Test
    void mapsSuccessfulDisplayState() throws Exception {
        when(service.getState(any())).thenReturn(success());

        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.storeTime.timezone").value("Asia/Singapore"))
            .andExpect(jsonPath("$.storeTime.timeText").value("10:30"))
            .andExpect(jsonPath("$.currentCall.queueTicketId").value(TICKET_ID.toString()))
            .andExpect(jsonPath("$.currentCall.displayNumber").value("A7"))
            .andExpect(jsonPath("$.waiting.count").value(1))
            .andExpect(jsonPath("$.ads.statePollSeconds").value(3))
            .andExpect(jsonPath("$.ads.slides[0].title").value("欢迎光临"));

        ArgumentCaptor<QueueDisplayQuery> captor = ArgumentCaptor.forClass(QueueDisplayQuery.class);
        verify(service).getState(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().storeId()).isEqualTo(STORE_ID);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void forbidsMissingRolePermissionAndStoreScope() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("queue.display.view"), Set.of(STORE_ID));
        mockMvc.perform(get(ENDPOINT, STORE_ID)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = actor(Set.of("store_staff"), Set.of("queue.view"), Set.of(STORE_ID));
        mockMvc.perform(get(ENDPOINT, STORE_ID)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = actor(Set.of("tenant_admin"), Set.of("queue.display.view"), Set.of());
        mockMvc.perform(get(ENDPOINT, OTHER_STORE_ID)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(service);
    }

    @Test
    void endpointDeclaresQueueDisplayViewAppGatePermission() throws Exception {
        Method method = QueueDisplayController.class.getMethod("getState", UUID.class);

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("queue.display.view");
    }

    private static QueueDisplayResult success() {
        return QueueDisplayResult.success(
            Instant.parse("2030-06-20T02:30:00Z"),
            "Asia/Singapore",
            "10:30",
            LocalDate.of(2030, 6, 20),
            new QueueDisplayCurrentCall(
                TICKET_ID,
                7,
                "1-2",
                "赵先生",
                2,
                Instant.parse("2030-06-20T02:29:00Z"),
                Instant.parse("2030-06-20T02:33:00Z")
            ).withDisplayValues("A7", "赵先生"),
            1,
            List.of(new QueueDisplayWaitingPreviewItem(8, "1-2", "钱女士", 2).withDisplayValues("A8", "钱女士")),
            new QueueDisplayAds("text", 5, 3, List.of(new QueueDisplayAdSlide("slide-1", "欢迎光临", "食刻", "标语")))
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
