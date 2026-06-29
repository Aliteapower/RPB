package com.rpb.reservation.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.reservation.application.ReservationCalendarSummaryResult;
import com.rpb.reservation.reservation.application.ReservationTimeSlot;
import com.rpb.reservation.reservation.application.ReservationTimeSlotListResult;
import com.rpb.reservation.reservation.application.ReservationTodayViewItem;
import com.rpb.reservation.reservation.application.ReservationTodayViewResult;
import com.rpb.reservation.reservation.application.query.ReservationCalendarSummaryQuery;
import com.rpb.reservation.reservation.application.query.ReservationTimeSlotQuery;
import com.rpb.reservation.reservation.application.query.ReservationTodayViewQuery;
import com.rpb.reservation.reservation.application.service.ReservationTodayViewApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReservationTodayViewControllerTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009701");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000009701");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000009701");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000009701");
    private static final UUID SEATING_ID = UUID.fromString("60000000-0000-0000-0000-000000009701");
    private static final UUID RESOURCE_ID = UUID.fromString("70000000-0000-0000-0000-000000009701");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000009701");

    private CapturingReservationTodayViewService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = new CapturingReservationTodayViewService();
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("reservation.today_view"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReservationTodayViewController(
                applicationService,
                actorProvider,
                new ReservationTodayViewApiMapper(),
                new ReservationTodayViewApiErrorMapper()
            ))
            .build();
    }

    @Test
    void todayViewEndpointRequiresReservationTodayViewAppGatePermission() throws Exception {
        Method method = ReservationTodayViewController.class.getMethod(
            "todayReservations",
            UUID.class,
            String.class,
            String.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.today_view");
    }

    @Test
    void calendarSummaryEndpointRequiresReservationTodayViewAppGatePermission() throws Exception {
        Method method = ReservationTodayViewController.class.getMethod(
            "calendarSummary",
            UUID.class,
            String.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.today_view");
    }

    @Test
    void timeSlotsEndpointRequiresReservationCreateAppGatePermission() throws Exception {
        Method method = ReservationTodayViewController.class.getMethod(
            "timeSlots",
            UUID.class,
            String.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.create");
    }

    @Test
    void mapsOptionalQueryParamsToReadOnlyApplicationQueryAndResponse() throws Exception {
        applicationService.todayResult = ReservationTodayViewResult.success(
            STORE_ID,
            LocalDate.parse("2030-06-20"),
            "Asia/Singapore",
            "operational",
            List.of(new ReservationTodayViewItem(
                RESERVATION_ID,
                "R-TV-9701",
                "confirmed",
                4,
                Instant.parse("2030-06-20T03:00:00Z"),
                Instant.parse("2030-06-20T04:30:00Z"),
                Instant.parse("2030-06-20T03:15:00Z"),
                LocalDate.parse("2030-06-20"),
                "Today Guest",
                "VIP",
                "****4567",
                "Window seat",
                SEATING_ID,
                "dining_table",
                RESOURCE_ID,
                "A01",
                "dining_table",
                RESOURCE_ID,
                "A01",
                QUEUE_TICKET_ID,
                9,
                "called"
            ))
        );

        mockMvc.perform(get("/api/v1/stores/{storeId}/reservations/today", STORE_ID)
                .param("businessDate", "2030-06-20")
                .param("status", "operational"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.storeId").value(STORE_ID.toString()))
            .andExpect(jsonPath("$.businessDate").value("2030-06-20"))
            .andExpect(jsonPath("$.storeTimezone").value("Asia/Singapore"))
            .andExpect(jsonPath("$.statusFilter").value("operational"))
            .andExpect(jsonPath("$.items[0].reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.items[0].reservationCode").value("R-TV-9701"))
            .andExpect(jsonPath("$.items[0].status").value("confirmed"))
            .andExpect(jsonPath("$.items[0].phoneMasked").value("****4567"))
            .andExpect(jsonPath("$.items[0].phoneE164").doesNotExist())
            .andExpect(jsonPath("$.items[0].seatingId").value(SEATING_ID.toString()))
            .andExpect(jsonPath("$.items[0].currentResourceType").value("dining_table"))
            .andExpect(jsonPath("$.items[0].currentResourceId").value(RESOURCE_ID.toString()))
            .andExpect(jsonPath("$.items[0].currentResourceCode").value("A01"))
            .andExpect(jsonPath("$.items[0].assignedResourceType").value("dining_table"))
            .andExpect(jsonPath("$.items[0].assignedResourceId").value(RESOURCE_ID.toString()))
            .andExpect(jsonPath("$.items[0].assignedResourceCode").value("A01"))
            .andExpect(jsonPath("$.items[0].queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.items[0].queueTicketNumber").value(9))
            .andExpect(jsonPath("$.items[0].queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.idempotency").doesNotExist());

        ReservationTodayViewQuery query = applicationService.todayQuery;
        assertThat(query.tenantId()).isEqualTo(TENANT_ID);
        assertThat(query.storeId()).isEqualTo(STORE_ID);
        assertThat(query.actorId()).isEqualTo(ACTOR_ID);
        assertThat(query.actorType()).isEqualTo("staff");
        assertThat(query.businessDate()).isEqualTo("2030-06-20");
        assertThat(query.status()).isEqualTo("operational");
    }

    @Test
    void rejectsMissingControllerPermissionBeforeApplicationQuery() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("reservation.create"), Set.of(STORE_ID));

        mockMvc.perform(get("/api/v1/stores/{storeId}/reservations/today", STORE_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.forbidden"));

        assertThat(applicationService.todayQuery).isNull();
    }

    @Test
    void mapsTimeSlotsQueryAndResponse() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("reservation.create"), Set.of(STORE_ID));
        applicationService.timeSlotResult = ReservationTimeSlotListResult.success(
            STORE_ID,
            LocalDate.parse("2030-06-20"),
            "Asia/Singapore",
            List.of(new ReservationTimeSlot(
                UUID.fromString("9d81f2ab-f8de-4b8a-bc77-58bb7b026001"),
                "lunch",
                "午餐",
                LocalDate.parse("2030-06-20"),
                java.time.LocalTime.parse("11:00"),
                Instant.parse("2030-06-20T03:00:00Z"),
                false,
                true
            ))
        );

        mockMvc.perform(get("/api/v1/stores/{storeId}/reservations/time-slots", STORE_ID)
                .param("businessDate", "2030-06-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.storeId").value(STORE_ID.toString()))
            .andExpect(jsonPath("$.businessDate").value("2030-06-20"))
            .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
            .andExpect(jsonPath("$.slots[0].periodKey").value("lunch"))
            .andExpect(jsonPath("$.slots[0].time").value("11:00"))
            .andExpect(jsonPath("$.slots[0].startAt").value("2030-06-20T03:00:00Z"))
            .andExpect(jsonPath("$.slots[0].selectable").value(true));

        ReservationTimeSlotQuery query = applicationService.timeSlotQuery;
        assertThat(query.tenantId()).isEqualTo(TENANT_ID);
        assertThat(query.storeId()).isEqualTo(STORE_ID);
        assertThat(query.actorId()).isEqualTo(ACTOR_ID);
        assertThat(query.actorType()).isEqualTo("staff");
        assertThat(query.businessDate()).isEqualTo("2030-06-20");
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
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

    private static final class CapturingReservationTodayViewService extends ReservationTodayViewApplicationService {
        private ReservationTodayViewQuery todayQuery;
        private ReservationCalendarSummaryQuery calendarSummaryQuery;
        private ReservationTimeSlotQuery timeSlotQuery;
        private ReservationTodayViewResult todayResult;
        private ReservationTimeSlotListResult timeSlotResult;

        private CapturingReservationTodayViewService() {
            super(null, null);
        }

        @Override
        public ReservationTodayViewResult getToday(ReservationTodayViewQuery query) {
            this.todayQuery = query;
            return todayResult;
        }

        @Override
        public ReservationCalendarSummaryResult getCalendarSummary(ReservationCalendarSummaryQuery query) {
            this.calendarSummaryQuery = query;
            throw new AssertionError("Calendar summary should not be invoked by this controller test");
        }

        @Override
        public ReservationTimeSlotListResult getTimeSlots(ReservationTimeSlotQuery query) {
            this.timeSlotQuery = query;
            return timeSlotResult;
        }
    }
}
