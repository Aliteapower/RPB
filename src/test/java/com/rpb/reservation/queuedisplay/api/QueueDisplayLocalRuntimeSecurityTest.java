package com.rpb.reservation.queuedisplay.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.queuedisplay.application.QueueDisplayAds;
import com.rpb.reservation.queuedisplay.application.QueueDisplayApplicationService;
import com.rpb.reservation.queuedisplay.application.QueueDisplayResult;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QueueDisplayController.class)
@Import({
    AppGateApiErrorMapper.class,
    QueueDisplayApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000976",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000976",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=queue.display.view",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000976"
})
class QueueDisplayLocalRuntimeSecurityTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000976");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000976");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueDisplayApplicationService service;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileQueueDisplayRequestWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "queue.display.view"
        ));
        when(service.getState(any())).thenReturn(QueueDisplayResult.success(
            Instant.parse("2030-06-20T02:30:00Z"),
            "Asia/Singapore",
            "10:30",
            LocalDate.of(2030, 6, 20),
            null,
            0,
            List.of(),
            new QueueDisplayAds("text", 5, 3, List.of())
        ));

        mockMvc.perform(get("/api/v1/stores/{storeId}/queue-display/state", STORE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.ads.statePollSeconds").value(3));

        verify(service).getState(any());
    }
}
