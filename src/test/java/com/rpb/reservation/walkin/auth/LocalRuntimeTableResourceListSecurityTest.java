package com.rpb.reservation.walkin.auth;

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
import com.rpb.reservation.table.api.TableResourceListApiErrorMapper;
import com.rpb.reservation.table.api.TableResourceListController;
import com.rpb.reservation.table.application.TableResourceItem;
import com.rpb.reservation.table.application.TableResourceListResult;
import com.rpb.reservation.table.application.service.TableResourceListApplicationService;
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

@WebMvcTest(TableResourceListController.class)
@Import({
    AppGateApiErrorMapper.class,
    TableResourceListApiErrorMapper.class,
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
    "rpb.local-auth.permissions[0]=table.view",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000981"
})
class LocalRuntimeTableResourceListSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/tables";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000000981");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TableResourceListApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileTableResourceRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "table.view"
        ));
        when(applicationService.listResources(any())).thenReturn(TableResourceListResult.success(List.of(
            new TableResourceItem(
                "dining_table",
                TABLE_ID,
                "A01",
                "A01",
                "大厅",
                1,
                4,
                "available",
                true,
                null,
                List.of()
            )
        )));

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("partySize", "4").param("includeGroups", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[0].code").value("A01"));

        verify(applicationService).listResources(any());
    }
}
