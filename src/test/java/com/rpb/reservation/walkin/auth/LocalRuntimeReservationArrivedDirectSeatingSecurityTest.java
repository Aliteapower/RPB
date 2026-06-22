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
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingResult;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
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
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000901",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000901",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=reservation.seat",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000901"
})
class LocalRuntimeReservationArrivedDirectSeatingSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000901");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000901");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000901");
    private static final UUID TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000901");
    private static final UUID SEATING_ID = UUID.fromString("80000000-0000-0000-0000-000000000901");

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
    void acceptsLocalProfileSeatRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "reservation.seat"
        ));
        when(seatingApplicationService.seatArrivedReservation(any())).thenReturn(ReservationArrivedDirectSeatingResult.success(
            RESERVATION_ID,
            "R-SEAT-0901",
            SEATING_ID,
            "dining_table",
            TABLE_ID,
            4,
            "occupied",
            List.of(),
            List.of(TABLE_ID),
            "completed",
            List.of("reservation.seated", "seating.created", "table.occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "local-runtime-reservation-seat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tableId": "60000000-0000-0000-0000-000000000901",
                      "note": "Window table"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        verify(seatingApplicationService).seatArrivedReservation(any());
        verifyNoInteractions(createApplicationService);
        verifyNoInteractions(checkInApplicationService);
    }
}
