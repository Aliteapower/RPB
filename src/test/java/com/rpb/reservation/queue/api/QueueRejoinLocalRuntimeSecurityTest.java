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
import com.rpb.reservation.queue.application.QueueRejoinResult;
import com.rpb.reservation.queue.application.service.QueueRejoinApplicationService;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
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

@WebMvcTest(QueueRejoinController.class)
@Import({
    AppGateApiErrorMapper.class,
    QueueRejoinApiMapper.class,
    QueueRejoinApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000986",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000986",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=queue.rejoin",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000986"
})
class QueueRejoinLocalRuntimeSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000986");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000986");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000986");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000986");
    private static final Instant REJOINED_AT = Instant.parse("2030-06-20T03:55:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueRejoinApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileQueueRejoinRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "queue.rejoin"
        ));
        when(applicationService.rejoinQueueTicket(any())).thenReturn(QueueRejoinResult.success(
            QUEUE_TICKET_ID,
            12,
            42,
            RESERVATION_ID,
            "R-REJOIN-0986",
            REJOINED_AT,
            "completed",
            List.of("queue_ticket.rejoined"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "local-runtime-queue-rejoin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.queuePosition").value(42))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.rejoinedAt").value(REJOINED_AT.toString()))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        verify(applicationService).rejoinQueueTicket(any());
    }
}
