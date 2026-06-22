package com.rpb.reservation.queue.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.queue.application.SeatingFromCalledQueueResult;
import com.rpb.reservation.queue.application.service.SeatingFromCalledQueueApplicationService;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
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

@WebMvcTest(SeatingFromCalledQueueController.class)
@Import({
    AppGateApiErrorMapper.class,
    SeatingFromCalledQueueApiMapper.class,
    SeatingFromCalledQueueApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000991",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000991",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=queue.seat",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000991"
})
class SeatingFromCalledQueueLocalRuntimeSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000991");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000991");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000991");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000991");
    private static final UUID TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000991");
    private static final UUID SEATING_ID = UUID.fromString("80000000-0000-0000-0000-000000000991");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeatingFromCalledQueueApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileQueueSeatRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "queue.seat"
        ));
        when(applicationService.seatCalledQueueTicket(any())).thenReturn(SeatingFromCalledQueueResult.success(
            QUEUE_TICKET_ID,
            12,
            RESERVATION_ID,
            "R-QSEAT-0991",
            SEATING_ID,
            "dining_table",
            TABLE_ID,
            4,
            "occupied",
            List.of(),
            List.of(TABLE_ID),
            "completed",
            List.of("queue_ticket.seated", "reservation.seated", "seating.created", "table.occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "local-runtime-queue-seat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tableId": "%s",
                      "tableGroupId": null,
                      "note": "Seat called queue ticket"
                    }
                    """.formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        verify(applicationService).seatCalledQueueTicket(any());
    }
}
