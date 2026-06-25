package com.rpb.reservation.appgate.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateAccessRequest;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.cleaning.api.CleaningController;
import com.rpb.reservation.queue.api.CallQueueTicketRequest;
import com.rpb.reservation.queue.api.QueueCallController;
import com.rpb.reservation.queue.api.QueueTicketListController;
import com.rpb.reservation.queue.api.SeatCalledQueueTicketRequest;
import com.rpb.reservation.queue.api.SeatingFromCalledQueueController;
import com.rpb.reservation.reservation.api.CreateReservationRequest;
import com.rpb.reservation.reservation.api.QueueArrivedReservationRequest;
import com.rpb.reservation.reservation.api.ReservationController;
import com.rpb.reservation.reservation.api.SeatArrivedReservationRequest;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.walkin.api.SeatWalkInDirectlyRequest;
import com.rpb.reservation.walkin.api.WalkInDirectSeatingController;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class AppGateGuardIntegrationTest {
    private static final String APP_KEY = "reservation_queue";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000008201");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000008201");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000008201");

    @Test
    void protectedReservationQueueEndpointsDeclareAppGateAnnotations() throws Exception {
        assertAnnotation(
            ReservationController.class.getMethod("createReservation", UUID.class, String.class, CreateReservationRequest.class),
            "reservation.create"
        );
        assertAnnotation(
            ReservationController.class.getMethod("seatArrivedReservation", UUID.class, UUID.class, String.class, SeatArrivedReservationRequest.class),
            "reservation.seat"
        );
        assertAnnotation(
            ReservationController.class.getMethod("queueArrivedReservation", UUID.class, UUID.class, String.class, QueueArrivedReservationRequest.class),
            "reservation.queue"
        );
        assertAnnotation(
            QueueCallController.class.getMethod("callQueueTicket", UUID.class, UUID.class, String.class, CallQueueTicketRequest.class),
            "queue.call"
        );
        assertAnnotation(
            QueueTicketListController.class.getMethod(
                "listQueueTickets",
                UUID.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
            ),
            "queue.view"
        );
        assertAnnotation(
            SeatingFromCalledQueueController.class.getMethod(
                "seatCalledQueueTicket",
                UUID.class,
                UUID.class,
                String.class,
                SeatCalledQueueTicketRequest.class
            ),
            "queue.seat"
        );
        assertAnnotation(
            WalkInDirectSeatingController.class.getMethod("seatWalkInDirectly", UUID.class, String.class, SeatWalkInDirectlyRequest.class),
            "walkin.direct_seating.create"
        );
        assertAnnotation(
            CleaningController.class.getMethod(
                "startCleaning",
                UUID.class,
                UUID.class,
                String.class,
                com.rpb.reservation.cleaning.api.StartCleaningRequest.class
            ),
            "cleaning.start"
        );
        assertAnnotation(
            CleaningController.class.getMethod(
                "completeCleaning",
                UUID.class,
                UUID.class,
                String.class,
                com.rpb.reservation.cleaning.api.CompleteCleaningRequest.class
            ),
            "cleaning.complete"
        );
    }

    @Test
    void annotatedEndpointReturnsAppGateErrorBeforeHandlerWhenDenied() throws Exception {
        AppGateService service = mock(AppGateService.class);
        when(service.evaluate(any())).thenReturn(AppGateDecision.deny(
            APP_KEY,
            TENANT_ID,
            STORE_ID,
            "reservation.create",
            AppGateDenyReason.STORE_APP_NOT_ENABLED
        ));

        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DemoProtectedController())
            .addInterceptors(interceptor(service, mock(AppGateDenialAuditService.class)))
            .build();

        mockMvc.perform(post("/api/v1/stores/{storeId}/demo-protected", STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STORE_APP_NOT_ENABLED"))
            .andExpect(jsonPath("$.error.messageKey").value("appgate.store_app_not_enabled"))
            .andExpect(jsonPath("$.error.details").isMap());
    }

    @Test
    void annotatedEndpointAuditsAppGateDenialBeforeReturningError() throws Exception {
        AppGateService service = mock(AppGateService.class);
        AppGateDenialAuditService auditService = mock(AppGateDenialAuditService.class);
        AppGateDecision decision = AppGateDecision.deny(
            APP_KEY,
            TENANT_ID,
            STORE_ID,
            "reservation.create",
            AppGateDenyReason.STORE_APP_NOT_ENABLED
        );
        when(service.evaluate(any())).thenReturn(decision);

        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DemoProtectedController())
            .addInterceptors(interceptor(service, auditService))
            .build();

        mockMvc.perform(post("/api/v1/stores/{storeId}/demo-protected", STORE_ID))
            .andExpect(status().isForbidden());

        verify(auditService).recordDenial(any(AppGateDecision.class), any(CurrentActor.class));
    }

    @Test
    void annotatedEndpointContinuesToHandlerWhenAllowed() throws Exception {
        AppGateService service = mock(AppGateService.class);
        when(service.evaluate(any())).thenReturn(AppGateDecision.allow(APP_KEY, TENANT_ID, STORE_ID, "reservation.create"));

        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DemoProtectedController())
            .addInterceptors(interceptor(service, mock(AppGateDenialAuditService.class)))
            .build();

        mockMvc.perform(post("/api/v1/stores/{storeId}/demo-protected", STORE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    private static void assertAnnotation(Method method, String permission) {
        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo(APP_KEY);
        assertThat(annotation.permission()).isEqualTo(permission);
    }

    private static AppGateInterceptor interceptor(AppGateService service, AppGateDenialAuditService auditService) {
        CurrentActor actor = CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("reservation.create"),
            Set.of(STORE_ID)
        );
        CurrentActorProvider actorProvider = () -> Optional.of(actor);
        return new AppGateInterceptor(service, auditService, actorProvider, new AppGateApiErrorMapper(), new ObjectMapper());
    }

    @RestController
    @RequestMapping("/api/v1/stores/{storeId}")
    private static final class DemoProtectedController {
        @PostMapping("/demo-protected")
        @RequireAppGate(appKey = APP_KEY, permission = "reservation.create")
        ResponseEntity<?> protectedCommand(@PathVariable UUID storeId) {
            return ResponseEntity.ok(java.util.Map.of("ok", true, "storeId", storeId));
        }
    }
}
