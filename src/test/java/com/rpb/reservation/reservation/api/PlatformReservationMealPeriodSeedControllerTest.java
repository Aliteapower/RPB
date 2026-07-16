package com.rpb.reservation.reservation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodManagementService;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodServiceErrorCode;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodServiceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformReservationMealPeriodSeedControllerTest {
    private static final String ENDPOINT = "/api/v1/platform/reservation/meal-period-seed";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000981");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ReservationMealPeriodManagementService service;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReservationMealPeriodManagementService.class);
        actorProvider = new MutableCurrentActorProvider(actor(
            Set.of("platform_admin"),
            Set.of("platform.reservation_meal_period.manage")
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(new PlatformReservationMealPeriodSeedController(service, actorProvider)).build();
    }

    @Test
    void mapsGetAndPatchPlatformMealPeriodSeeds() throws Exception {
        when(service.getPlatformSeedPeriods()).thenReturn(List.of(lunch()));
        when(service.replacePlatformSeedPeriods(any())).thenReturn(List.of(dinner()));

        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.periods[0].periodKey").value("lunch"))
            .andExpect(jsonPath("$.periods[0].startLocalTime").value("11:00"));

        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new PlatformReservationMealPeriodSeedRequest(List.of(
                    new ReservationMealPeriodRequest(
                        "dinner",
                        "Dinner",
                        "17:00",
                        "00:30",
                        true,
                        30,
                        "active",
                        20,
                        1
                    )
                )))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.periods[0].periodKey").value("dinner"))
            .andExpect(jsonPath("$.periods[0].endLocalTime").value("00:30"))
            .andExpect(jsonPath("$.periods[0].crossesNextDay").value(true));

        verify(service).getPlatformSeedPeriods();
        verify(service).replacePlatformSeedPeriods(any());
    }

    @Test
    void requiresPlatformAdminRoleAndMealPeriodManagePermission() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("platform.reservation_meal_period.manage"));
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = actor(Set.of("platform_admin"), Set.of("platform.tenant.manage"));
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = null;
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsInvalidRequestsToStableError() throws Exception {
        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "periods": [
                        {
                          "periodKey": "lunch",
                          "displayName": "Lunch",
                          "startLocalTime": "bad-time",
                          "endLocalTime": "15:00",
                          "crossesNextDay": false,
                          "slotIntervalMinutes": 30,
                          "status": "active",
                          "sortOrder": 10
                        }
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));

        doThrow(new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID))
            .when(service).replacePlatformSeedPeriods(any());

        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new PlatformReservationMealPeriodSeedRequest(List.of(
                    new ReservationMealPeriodRequest("lunch", "Lunch", "11:00", "15:00", false, 30, "active", 10, 0)
                )))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
    }

    @Test
    void mapsPersistenceErrorsToStableCode() throws Exception {
        when(service.getPlatformSeedPeriods()).thenThrow(new DataAccessResourceFailureException("db_down"));

        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("PERSISTENCE_ERROR"));
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

    private static ReservationMealPeriod dinner() {
        return new ReservationMealPeriod(
            UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026002"),
            "dinner",
            "Dinner",
            LocalTime.of(17, 0),
            LocalTime.of(0, 30),
            true,
            30,
            "active",
            20,
            1
        );
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions) {
        return CurrentActor.storeStaff(TENANT_ID, ACTOR_ID, "platform_admin", roles, permissions, Set.of());
    }

    private static final class MutableCurrentActorProvider implements CurrentActorProvider {
        private CurrentActor actor;

        private MutableCurrentActorProvider(CurrentActor actor) {
            this.actor = actor;
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(actor);
        }
    }
}
