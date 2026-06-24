package com.rpb.reservation.walkin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.reservation.api.ReservationApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationApiMapper;
import com.rpb.reservation.reservation.api.ReservationArrivedDirectSeatingApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationArrivedDirectSeatingApiMapper;
import com.rpb.reservation.reservation.api.ReservationArrivedToQueueApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationArrivedToQueueApiMapper;
import com.rpb.reservation.reservation.api.ReservationCancelApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationCancelApiMapper;
import com.rpb.reservation.reservation.api.ReservationCheckInApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationCheckInApiMapper;
import com.rpb.reservation.reservation.api.ReservationCompleteApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationCompleteApiMapper;
import com.rpb.reservation.reservation.api.ReservationController;
import com.rpb.reservation.reservation.api.ReservationNoShowApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationNoShowApiMapper;
import com.rpb.reservation.reservation.application.ReservationCancelResult;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCancelApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCompleteApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationNoShowApplicationService;
import java.time.Instant;
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

@WebMvcTest(ReservationController.class)
@Import({
    AppGateApiErrorMapper.class,
    ReservationApiMapper.class,
    ReservationApiErrorMapper.class,
    ReservationCheckInApiMapper.class,
    ReservationCheckInApiErrorMapper.class,
    ReservationArrivedDirectSeatingApiMapper.class,
    ReservationArrivedDirectSeatingApiErrorMapper.class,
    ReservationArrivedToQueueApiMapper.class,
    ReservationArrivedToQueueApiErrorMapper.class,
    ReservationCancelApiMapper.class,
    ReservationCancelApiErrorMapper.class,
    ReservationNoShowApiMapper.class,
    ReservationNoShowApiErrorMapper.class,
    ReservationCompleteApiMapper.class,
    ReservationCompleteApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000987",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000987",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=reservation.cancel",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000987"
})
class LocalRuntimeReservationCancelSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/cancel";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000987");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000987");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000987");
    private static final Instant CANCELLED_AT = Instant.parse("2030-06-20T03:20:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationCreateApplicationService createApplicationService;

    @MockBean
    private ReservationCheckInApplicationService checkInApplicationService;

    @MockBean
    private ReservationArrivedDirectSeatingApplicationService seatingApplicationService;

    @MockBean
    private ReservationArrivedToQueueApplicationService queueApplicationService;

    @MockBean
    private ReservationCancelApplicationService cancelApplicationService;

    @MockBean
    private ReservationNoShowApplicationService noShowApplicationService;

    @MockBean
    private ReservationCompleteApplicationService completeApplicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileCancelRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "reservation.cancel"
        ));
        when(cancelApplicationService.cancelReservation(any())).thenReturn(ReservationCancelResult.success(
            RESERVATION_ID,
            "R-CANCEL-0987",
            "cancelled",
            CANCELLED_AT,
            "guest_requested",
            "completed",
            List.of("reservation.cancelled"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "local-runtime-reservation-cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cancelledAt": "2030-06-20T03:20:00Z",
                      "reasonCode": "guest_requested",
                      "note": "Guest requested cancellation"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("cancelled"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        verify(cancelApplicationService).cancelReservation(any());
        verifyNoInteractions(createApplicationService);
        verifyNoInteractions(checkInApplicationService);
        verifyNoInteractions(seatingApplicationService);
        verifyNoInteractions(queueApplicationService);
    }
}
