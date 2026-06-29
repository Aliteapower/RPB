package com.rpb.reservation.reservation.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodManagementService;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.time.LocalTime;
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

@WebMvcTest(PlatformReservationMealPeriodSeedController.class)
@Import({
    AppGateApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000983",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000901",
    "rpb.local-auth.actor-type=platform_admin",
    "rpb.local-auth.roles[0]=platform_admin",
    "rpb.local-auth.permissions[0]=platform.reservation_meal_period.manage"
})
class PlatformReservationMealPeriodSeedLocalRuntimeSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationMealPeriodManagementService service;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void localRuntimeAllowsPlatformReservationMealPeriodSeedApiWhenConfiguredActorHasManagePermission() throws Exception {
        when(service.getPlatformSeedPeriods()).thenReturn(List.of(lunch()));

        mockMvc.perform(get("/api/v1/platform/reservation/meal-period-seed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.periods[0].periodKey").value("lunch"));
    }

    @Test
    void localRuntimeRejectsPlatformReservationMealPeriodSeedApiWithoutManagePermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/reservation/meal-period-seed")
                .header("X-Test-Actor-Type", "platform_admin")
                .header("X-Test-Actor-Role", "platform_admin")
                .header("X-Test-Permissions", "platform.tenant.manage"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private static ReservationMealPeriod lunch() {
        return new ReservationMealPeriod(
            UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026001"),
            "lunch",
            "Lunch",
            LocalTime.of(11, 0),
            LocalTime.of(15, 0),
            false,
            30,
            "active",
            10,
            1
        );
    }
}
