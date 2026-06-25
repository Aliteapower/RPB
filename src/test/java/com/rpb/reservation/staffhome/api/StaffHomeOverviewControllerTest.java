package com.rpb.reservation.staffhome.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.staffhome.application.StaffHomeOverview;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewError;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewQuery;
import com.rpb.reservation.staffhome.application.StaffHomeOverviewResult;
import com.rpb.reservation.staffhome.application.service.StaffHomeOverviewApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StaffHomeOverviewControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/staff-home/overview";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000002302");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000002302");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000002399");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000002302");

    private StaffHomeOverviewApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(StaffHomeOverviewApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("reservation.today_view"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new StaffHomeOverviewController(
                applicationService,
                actorProvider,
                new StaffHomeOverviewApiMapper(),
                new StaffHomeOverviewApiErrorMapper()
            ))
            .build();
    }

    @Test
    void returnsTodayOperationalOverview() throws Exception {
        when(applicationService.getOverview(any())).thenReturn(success());

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", "2030-06-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.storeId").value(STORE_ID.toString()))
            .andExpect(jsonPath("$.businessDate").value("2030-06-20"))
            .andExpect(jsonPath("$.storeTimezone").value("Asia/Singapore"))
            .andExpect(jsonPath("$.reservation.totalReservations").value(6))
            .andExpect(jsonPath("$.reservation.arrivedReservations").value(2))
            .andExpect(jsonPath("$.queue.waitingTickets").value(3))
            .andExpect(jsonPath("$.queue.calledPartySize").value(8))
            .andExpect(jsonPath("$.tables.availableTables").value(4))
            .andExpect(jsonPath("$.tables.temporaryGroups").value(1))
            .andExpect(jsonPath("$.partySizeGroups[0].label").value("1-2"))
            .andExpect(jsonPath("$.partySizeGroups[0].groups").value(2));

        ArgumentCaptor<StaffHomeOverviewQuery> queryCaptor = ArgumentCaptor.forClass(StaffHomeOverviewQuery.class);
        verify(applicationService).getOverview(queryCaptor.capture());
        StaffHomeOverviewQuery query = queryCaptor.getValue();
        assertThat(query.tenantId()).isEqualTo(TENANT_ID);
        assertThat(query.storeId()).isEqualTo(STORE_ID);
        assertThat(query.actorId()).isEqualTo(ACTOR_ID);
        assertThat(query.actorType()).isEqualTo("staff");
        assertThat(query.businessDate()).isEqualTo("2030-06-20");
    }

    @Test
    void endpointReusesReservationTodayViewAppGatePermission() throws Exception {
        Method method = StaffHomeOverviewController.class.getMethod(
            "overview",
            UUID.class,
            String.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.today_view");
    }

    @Test
    void missingPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("queue.view"), Set.of(STORE_ID));

        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.messageKey").value("staff_home.overview.forbidden"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(get(ENDPOINT, OTHER_STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void mapsApplicationErrorsToStableApiErrors() throws Exception {
        when(applicationService.getOverview(any())).thenReturn(
            StaffHomeOverviewResult.failure(StaffHomeOverviewError.INVALID_BUSINESS_DATE)
        );

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", "bad-date"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_BUSINESS_DATE"))
            .andExpect(jsonPath("$.error.messageKey").value("staff_home.overview.invalid_business_date"));
    }

    private static StaffHomeOverviewResult success() {
        return StaffHomeOverviewResult.success(new StaffHomeOverview(
            STORE_ID,
            LocalDate.parse("2030-06-20"),
            "Asia/Singapore",
            new StaffHomeOverview.ReservationMetrics(6, 24, 2, 8, 1, 4, 1),
            new StaffHomeOverview.QueueMetrics(3, 7, 1, 8, 2, 1, 0, 0),
            new StaffHomeOverview.TableMetrics(8, 4, 1, 2, 1, 1),
            List.of(
                new StaffHomeOverview.PartySizeGroupMetrics("1-2", 2, 4),
                new StaffHomeOverview.PartySizeGroupMetrics("3-4", 1, 3),
                new StaffHomeOverview.PartySizeGroupMetrics("5-6", 0, 0),
                new StaffHomeOverview.PartySizeGroupMetrics("7+", 1, 8)
            )
        ));
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            roles.contains("customer") ? "customer" : "staff",
            roles,
            permissions,
            storeIds
        );
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
