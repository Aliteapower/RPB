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
import com.rpb.reservation.reservation.api.ReservationCheckInApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationCheckInApiMapper;
import com.rpb.reservation.reservation.api.ReservationController;
import com.rpb.reservation.reservation.application.ReservationArrivedToQueueResult;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import java.time.LocalDate;
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
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000971",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000971",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=reservation.queue",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000971"
})
class LocalRuntimeReservationArrivedToQueueSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/queue";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000971");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000971");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000971");
    private static final UUID QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000971");

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
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileQueueRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "reservation.queue"
        ));
        when(queueApplicationService.queueArrivedReservation(any())).thenReturn(ReservationArrivedToQueueResult.success(
            RESERVATION_ID,
            "R-QUEUE-0971",
            QUEUE_TICKET_ID,
            7,
            QUEUE_GROUP_ID,
            "3-4",
            4,
            "3-4",
            LocalDate.parse("2030-06-20"),
            2,
            "completed",
            List.of("reservation.queued", "queue_ticket.created"),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "local-runtime-reservation-queue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySizeGroup": "3-4",
                      "reasonCode": "NO_TABLE_AVAILABLE",
                      "note": "Customer is waiting near entrance"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        verify(queueApplicationService).queueArrivedReservation(any());
        verifyNoInteractions(createApplicationService);
        verifyNoInteractions(checkInApplicationService);
        verifyNoInteractions(seatingApplicationService);
    }
}
