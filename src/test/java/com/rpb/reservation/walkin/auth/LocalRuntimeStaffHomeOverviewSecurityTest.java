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
import com.rpb.reservation.staffhome.api.StaffHomeOverviewApiErrorMapper;
import com.rpb.reservation.staffhome.api.StaffHomeOverviewApiMapper;
import com.rpb.reservation.staffhome.api.StaffHomeOverviewController;
import com.rpb.reservation.staffhome.application.StaffHomeOverview;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewResult;
import com.rpb.reservation.staffhome.application.service.StaffHomeOverviewApplicationService;
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

@WebMvcTest(StaffHomeOverviewController.class)
@Import({
    AppGateApiErrorMapper.class,
    StaffHomeOverviewApiMapper.class,
    StaffHomeOverviewApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000983",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000983",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=reservation.today_view",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000983"
})
class LocalRuntimeStaffHomeOverviewSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/staff-home/overview";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000983");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000983");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffHomeOverviewApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileStaffHomeOverviewWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "reservation.today_view"
        ));
        when(applicationService.getOverview(any())).thenReturn(StaffHomeOverviewResult.success(new StaffHomeOverview(
            STORE_ID,
            LocalDate.parse("2030-06-20"),
            "Asia/Singapore",
            new StaffHomeOverview.ReservationMetrics(0, 0, 0, 0, 0, 0, 0),
            new StaffHomeOverview.QueueMetrics(0, 0, 0, 0, 0, 0, 0, 0),
            new StaffHomeOverview.TableMetrics(0, 0, 0, 0, 0, 0),
            List.of()
        )));

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", "2030-06-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.businessDate").value("2030-06-20"));

        verify(applicationService).getOverview(any());
    }
}
