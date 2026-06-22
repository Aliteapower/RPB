package com.rpb.reservation.queue.api;

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
import com.rpb.reservation.queue.application.QueueTicketListPage;
import com.rpb.reservation.queue.application.QueueTicketListResult;
import com.rpb.reservation.queue.application.service.QueueTicketListApplicationService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QueueTicketListController.class)
@Import({
    AppGateApiErrorMapper.class,
    QueueTicketListApiMapper.class,
    QueueTicketListApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000992",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000992",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=queue.view",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000992"
})
class QueueTicketListLocalRuntimeSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000992");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000992");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueTicketListApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileQueueListRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "queue.view"
        ));
        when(applicationService.listQueueTickets(any())).thenReturn(QueueTicketListResult.success(
            List.of(),
            new QueueTicketListPage(50, 0, 0)
        ));

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("status", "waiting"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.page.limit").value(50));

        verify(applicationService).listQueueTickets(any());
    }
}
