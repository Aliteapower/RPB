package com.rpb.reservation.walkin.auth;

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
import com.rpb.reservation.table.api.TableSwitchApiErrorMapper;
import com.rpb.reservation.table.api.TableSwitchApiMapper;
import com.rpb.reservation.table.api.TableSwitchController;
import com.rpb.reservation.table.application.TableSwitchResult;
import com.rpb.reservation.table.application.service.TableSwitchApplicationService;
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

@WebMvcTest(TableSwitchController.class)
@Import({
    AppGateApiErrorMapper.class,
    TableSwitchApiMapper.class,
    TableSwitchApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000981",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000981",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=table.switch",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000981"
})
class LocalRuntimeTableSwitchSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/seatings/{seatingId}/table-switch";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");
    private static final UUID SEATING_ID = UUID.fromString("40000000-0000-0000-0000-000000000981");
    private static final UUID FROM_TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000981");
    private static final UUID TO_TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000982");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TableSwitchApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileTableSwitchRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "table.switch"
        ));
        when(applicationService.switchTable(any())).thenReturn(TableSwitchResult.success(
            SEATING_ID,
            "dining_table",
            FROM_TABLE_ID,
            "available",
            "dining_table",
            TO_TABLE_ID,
            "occupied",
            null,
            "occupied",
            "completed",
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-switch-local")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId":"%s","reasonCode":"guest_requested","note":"move"}
                    """.formatted(TO_TABLE_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.toResource.id").value(TO_TABLE_ID.toString()));

        verify(applicationService).switchTable(any());
    }
}
