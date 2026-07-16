package com.rpb.reservation.table.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.table.application.TableSwitchError;
import com.rpb.reservation.table.application.TableSwitchResult;
import com.rpb.reservation.table.application.command.SwitchTableCommand;
import com.rpb.reservation.table.application.service.TableSwitchApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TableSwitchControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/seatings/{seatingId}/table-switch";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SEATING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID FROM_TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID TO_TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TableSwitchApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(TableSwitchApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("table.switch"),
            Set.of(STORE_ID)
        ));
        TableSwitchApiMapper apiMapper = new TableSwitchApiMapper();
        TableSwitchApiErrorMapper errorMapper = new TableSwitchApiErrorMapper();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TableSwitchController(applicationService, actorProvider, apiMapper, errorMapper))
            .build();
    }

    @Test
    void switchesTableAndMapsCommand() throws Exception {
        when(applicationService.switchTable(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new TableSwitchRequest(TO_TABLE_ID, null, "guest_requested", "move table"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.seatingId").value(SEATING_ID.toString()))
            .andExpect(jsonPath("$.fromResource.type").value("TABLE"))
            .andExpect(jsonPath("$.fromResource.id").value(FROM_TABLE_ID.toString()))
            .andExpect(jsonPath("$.fromResource.status").value("available"))
            .andExpect(jsonPath("$.toResource.type").value("TABLE"))
            .andExpect(jsonPath("$.toResource.id").value(TO_TABLE_ID.toString()))
            .andExpect(jsonPath("$.toResource.status").value("occupied"))
            .andExpect(jsonPath("$.cleaningId").value(nullValue()))
            .andExpect(jsonPath("$.seatingStatus").value("occupied"))
            .andExpect(jsonPath("$.events[0]").value("table.switch.completed"))
            .andExpect(jsonPath("$.events[1]").value("table.available"))
            .andExpect(jsonPath("$.events[2]").value("table.occupied"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<SwitchTableCommand> commandCaptor = ArgumentCaptor.forClass(SwitchTableCommand.class);
        verify(applicationService).switchTable(commandCaptor.capture());
        SwitchTableCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.seatingId()).isEqualTo(SEATING_ID);
        assertThat(command.tableId()).isEqualTo(TO_TABLE_ID);
        assertThat(command.tableGroupId()).isNull();
        assertThat(command.idempotencyKey()).isEqualTo("idem-switch");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.reasonCode()).isEqualTo("guest_requested");
        assertThat(command.note()).isEqualTo("move table");
    }

    @Test
    void replayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.switchTable(any())).thenReturn(success(true));

        mockMvc.perform(post(ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new TableSwitchRequest(TO_TABLE_ID, null, null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.replayed").value(true));
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, SEATING_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new TableSwitchRequest(TO_TABLE_ID, null, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("table_switch.missing_idempotency_key"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("table.view"),
            Set.of(STORE_ID)
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new TableSwitchRequest(TO_TABLE_ID, null, null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        UUID otherStore = UUID.fromString("20000000-0000-0000-0000-000000000099");

        mockMvc.perform(post(ENDPOINT, otherStore, SEATING_ID)
                .header("Idempotency-Key", "idem-switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new TableSwitchRequest(TO_TABLE_ID, null, null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(TableSwitchError.SEATING_NOT_FOUND, 404, "SEATING_NOT_FOUND");
        assertApplicationError(TableSwitchError.SEATING_NOT_OCCUPIED, 409, "SEATING_NOT_OCCUPIED");
        assertApplicationError(TableSwitchError.ACTIVE_SEATING_RESOURCE_NOT_FOUND, 409, "ACTIVE_SEATING_RESOURCE_NOT_FOUND");
        assertApplicationError(TableSwitchError.TARGET_SAME_AS_CURRENT, 409, "TABLE_SWITCH_TARGET_SAME_AS_CURRENT");
        assertApplicationError(TableSwitchError.TABLE_NOT_AVAILABLE, 409, "TABLE_NOT_AVAILABLE");
        assertApplicationError(TableSwitchError.TABLE_GROUP_INVALID, 409, "TABLE_GROUP_INVALID");
        assertApplicationError(TableSwitchError.TABLE_CAPACITY_INSUFFICIENT, 409, "TABLE_CAPACITY_INSUFFICIENT");
        assertApplicationError(TableSwitchError.TABLE_LOCK_CONFLICT, 409, "TABLE_LOCK_CONFLICT");
        assertApplicationError(TableSwitchError.CLEANING_ALREADY_ACTIVE, 409, "CLEANING_ALREADY_ACTIVE");
        assertApplicationError(TableSwitchError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT");
        assertApplicationError(TableSwitchError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS");
        assertApplicationError(TableSwitchError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED");
        assertApplicationError(TableSwitchError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR");
    }

    @Test
    void controllerRequiresReservationQueueTableSwitchPermission() throws Exception {
        Method method = TableSwitchController.class.getMethod(
            "switchTable",
            UUID.class,
            UUID.class,
            String.class,
            TableSwitchRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("table.switch");
    }

    private void assertApplicationError(TableSwitchError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        when(applicationService.switchTable(any())).thenReturn(TableSwitchResult.failure(applicationError));

        mockMvc.perform(post(ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new TableSwitchRequest(TO_TABLE_ID, null, null, null))))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value(messageKey(expectedApiCode)))
            .andExpect(jsonPath("$.error.details").isMap());
    }

    private String json(TableSwitchRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private static TableSwitchResult success(boolean replayed) {
        if (replayed) {
            return TableSwitchResult.replay(
                SEATING_ID,
                "dining_table",
                FROM_TABLE_ID,
                "available",
                "dining_table",
                TO_TABLE_ID,
                "occupied",
                null,
                "occupied"
            );
        }
        return TableSwitchResult.success(
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
        );
    }

    private static String messageKey(String apiCode) {
        return switch (apiCode) {
            case "MISSING_IDEMPOTENCY_KEY" -> "table_switch.missing_idempotency_key";
            case "TABLE_SWITCH_TARGET_SAME_AS_CURRENT" -> "table_switch.target_same_as_current";
            case "TABLE_SWITCH_TARGET_INVALID" -> "table_switch.target_invalid";
            case "ACTIVE_SEATING_RESOURCE_NOT_FOUND" -> "table_switch.active_seating_resource_not_found";
            default -> "table_switch." + apiCode.toLowerCase();
        };
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
