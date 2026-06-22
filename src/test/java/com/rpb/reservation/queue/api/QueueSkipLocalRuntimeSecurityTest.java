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
import com.rpb.reservation.queue.application.QueueSkipResult;
import com.rpb.reservation.queue.application.service.QueueSkipApplicationService;
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

@WebMvcTest(QueueSkipController.class)
@Import({
    AppGateApiErrorMapper.class,
    QueueSkipApiMapper.class,
    QueueSkipApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000984",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000984",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=queue.skip",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000984"
})
class QueueSkipLocalRuntimeSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/skip";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000984");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000984");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000984");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000984");
    private static final Instant SKIPPED_AT = Instant.parse("2030-06-20T03:45:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueSkipApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileQueueSkipRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "queue.skip"
        ));
        when(applicationService.skipQueueTicket(any())).thenReturn(QueueSkipResult.success(
            QUEUE_TICKET_ID,
            12,
            RESERVATION_ID,
            "R-SKIP-0984",
            SKIPPED_AT,
            "completed",
            List.of("queue_ticket.skipped"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "local-runtime-queue-skip")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "skippedAt": "2030-06-20T03:45:00Z",
                      "reasonCode": "NO_RESPONSE",
                      "note": "Customer did not return"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketStatus").value("skipped"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.skippedAt").value(SKIPPED_AT.toString()))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        verify(applicationService).skipQueueTicket(any());
    }
}
