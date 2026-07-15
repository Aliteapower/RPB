package com.rpb.reservation.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.reservation.application.AssignableReservationTable;
import com.rpb.reservation.reservation.application.AssignableReservationTablesResult;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentError;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentResult;
import com.rpb.reservation.reservation.application.command.AssignReservationTableCommand;
import com.rpb.reservation.reservation.application.query.AssignableReservationTablesQuery;
import com.rpb.reservation.reservation.application.service.ReservationTableAssignmentApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReservationTableAssignmentControllerTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009801");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000009801");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000009802");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000009801");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000009801");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000009801");
    private static final String BASE = "/api/v1/stores/{storeId}/reservations/{reservationId}";

    private CapturingService service;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new CapturingService();
        actorProvider = new MutableCurrentActorProvider(actor(
            Set.of("store_staff"),
            Set.of("table.view", "reservation.create"),
            Set.of(STORE_ID)
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationTableAssignmentController(
            service,
            actorProvider,
            new ReservationTableAssignmentApiMapper(),
            new ReservationTableAssignmentApiErrorMapper()
        )).build();
    }

    @Test
    void endpointsDeclareRequiredAppGatePermissions() throws Exception {
        Method query = ReservationTableAssignmentController.class.getMethod(
            "listAssignableTables", UUID.class, UUID.class
        );
        Method command = ReservationTableAssignmentController.class.getMethod(
            "assignTable", UUID.class, UUID.class, String.class, AssignReservationTableRequest.class
        );

        assertThat(query.getAnnotation(RequireAppGate.class).permission()).isEqualTo("table.view");
        assertThat(command.getAnnotation(RequireAppGate.class).permission()).isEqualTo("reservation.create");
    }

    @Test
    void listsAssignableTablesForAuthorizedEmployee() throws Exception {
        service.queryResult = AssignableReservationTablesResult.success(
            RESERVATION_ID,
            2,
            List.of(new AssignableReservationTable(TABLE_ID, "A01", "Window A01", "Main", 1, 4))
        );

        mockMvc.perform(get(BASE + "/assignable-tables", STORE_ID, RESERVATION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.partySize").value(2))
            .andExpect(jsonPath("$.tables[0].tableId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.tables[0].tableCode").value("A01"))
            .andExpect(jsonPath("$.tables[0].areaName").value("Main"));

        assertThat(service.query.tenantId()).isEqualTo(TENANT_ID);
        assertThat(service.query.storeId()).isEqualTo(STORE_ID);
        assertThat(service.query.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(service.query.actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void assignsTableAndMapsIdempotency() throws Exception {
        service.commandResult = ReservationTableAssignmentResult.success(
            RESERVATION_ID,
            TABLE_ID,
            "A01",
            "completed",
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        mockMvc.perform(put(BASE + "/table-assignment", STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-table-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"%s\"}".formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.tableId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.tableCode").value("A01"))
            .andExpect(jsonPath("$.assignmentStatus").value("active"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertThat(service.command.idempotencyKey()).isEqualTo("assign-table-1");
        assertThat(service.command.source()).isEqualTo("staff");
    }

    @Test
    void rejectsMissingActorWrongRoleAndMissingPermission() throws Exception {
        actorProvider.actor = null;
        assertForbiddenQuery();

        actorProvider.actor = actor(Set.of("customer"), Set.of("table.view"), Set.of(STORE_ID));
        assertForbiddenQuery();

        actorProvider.actor = actor(Set.of("store_staff"), Set.of("reservation.create"), Set.of(STORE_ID));
        assertForbiddenQuery();
        assertThat(service.query).isNull();
    }

    @Test
    void rejectsWrongStoreBeforeApplicationService() throws Exception {
        mockMvc.perform(get(BASE + "/assignable-tables", OTHER_STORE_ID, RESERVATION_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        assertThat(service.query).isNull();
    }

    @Test
    void rejectsMissingIdempotencyKeyAndInvalidBody() throws Exception {
        mockMvc.perform(put(BASE + "/table-assignment", STORE_ID, RESERVATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"%s\"}".formatted(TABLE_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        mockMvc.perform(put(BASE + "/table-assignment", STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-table-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_COMMAND"));

        assertThat(service.command).isNull();
    }

    @Test
    void mapsStableConflictAndNotFoundErrors() throws Exception {
        service.commandResult = ReservationTableAssignmentResult.failure(
            ReservationTableAssignmentError.TABLE_NOT_AVAILABLE
        );
        mockMvc.perform(put(BASE + "/table-assignment", STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-table-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"%s\"}".formatted(TABLE_ID)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_NOT_AVAILABLE"));

        service.queryResult = AssignableReservationTablesResult.failure(
            ReservationTableAssignmentError.RESERVATION_NOT_FOUND
        );
        mockMvc.perform(get(BASE + "/assignable-tables", STORE_ID, RESERVATION_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void mapsCompletedReplay() throws Exception {
        service.commandResult = ReservationTableAssignmentResult.replay(RESERVATION_ID, TABLE_ID, "A01");

        mockMvc.perform(put(BASE + "/table-assignment", STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-table-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"%s\"}".formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));
    }

    private void assertForbiddenQuery() throws Exception {
        mockMvc.perform(get(BASE + "/assignable-tables", STORE_ID, RESERVATION_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(TENANT_ID, ACTOR_ID, "staff", roles, permissions, storeIds);
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

    private static final class CapturingService extends ReservationTableAssignmentApplicationService {
        private AssignableReservationTablesQuery query;
        private AssignReservationTableCommand command;
        private AssignableReservationTablesResult queryResult;
        private ReservationTableAssignmentResult commandResult;

        private CapturingService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public AssignableReservationTablesResult listAssignableTables(AssignableReservationTablesQuery query) {
            this.query = query;
            return queryResult;
        }

        @Override
        public ReservationTableAssignmentResult assignTable(AssignReservationTableCommand command) {
            this.command = command;
            return commandResult;
        }
    }
}
