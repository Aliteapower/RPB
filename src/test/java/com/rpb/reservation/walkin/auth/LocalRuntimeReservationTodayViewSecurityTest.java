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
import com.rpb.reservation.reservation.api.ReservationTodayViewApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationTodayViewApiMapper;
import com.rpb.reservation.reservation.api.ReservationTodayViewController;
import com.rpb.reservation.reservation.application.ReservationCalendarSummaryResult;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import com.rpb.reservation.reservation.application.service.ReservationTodayViewApplicationService;
import java.time.LocalDate;
import java.time.YearMonth;
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

@WebMvcTest(ReservationTodayViewController.class)
@Import({
    AppGateApiErrorMapper.class,
    ReservationTodayViewApiMapper.class,
    ReservationTodayViewApiErrorMapper.class,
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
    "rpb.local-auth.permissions[0]=reservation.today_view",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000000981"
})
class LocalRuntimeReservationTodayViewSecurityTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/today";
    private static final String CALENDAR_SUMMARY_ENDPOINT = "/api/v1/stores/{storeId}/reservations/calendar-summary";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationTodayViewApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void acceptsLocalProfileTodayViewRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "reservation.today_view"
        ));
        when(applicationService.getToday(any())).thenReturn(ReservationTodayViewResult.success(
            STORE_ID,
            LocalDate.parse("2030-06-20"),
            "Asia/Singapore",
            "operational",
            List.of()
        ));

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", "2030-06-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.statusFilter").value("operational"));

        verify(applicationService).getToday(any());
    }

    @Test
    void acceptsLocalProfileCalendarSummaryRequestWithoutJwtLoginWhenConfiguredActorHasPermission() throws Exception {
        when(appGateService.evaluate(any())).thenReturn(AppGateDecision.allow(
            "reservation_queue",
            TENANT_ID,
            STORE_ID,
            "reservation.today_view"
        ));
        when(applicationService.getCalendarSummary(any())).thenReturn(ReservationCalendarSummaryResult.success(
            STORE_ID,
            YearMonth.parse("2030-06"),
            "Asia/Singapore",
            List.of()
        ));

        mockMvc.perform(get(CALENDAR_SUMMARY_ENDPOINT, STORE_ID).param("month", "2030-06"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.month").value("2030-06"));

        verify(applicationService).getCalendarSummary(any());
    }
}
