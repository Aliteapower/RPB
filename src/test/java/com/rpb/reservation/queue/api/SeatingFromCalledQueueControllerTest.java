package com.rpb.reservation.queue.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queue.application.SeatingFromCalledQueueError;
import com.rpb.reservation.queue.application.SeatingFromCalledQueueResult;
import com.rpb.reservation.queue.application.command.SeatCalledQueueTicketCommand;
import com.rpb.reservation.queue.application.service.SeatingFromCalledQueueApplicationService;
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

class SeatingFromCalledQueueControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/seating/direct";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000991");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000991");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000999");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000991");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000991");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000991");
    private static final UUID TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000991");
    private static final UUID TABLE_GROUP_ID = UUID.fromString("70000000-0000-0000-0000-000000000991");
    private static final UUID SEATING_ID = UUID.fromString("80000000-0000-0000-0000-000000000991");

    private SeatingFromCalledQueueApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(SeatingFromCalledQueueApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("queue.seat"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SeatingFromCalledQueueController(
                applicationService,
                actorProvider,
                new SeatingFromCalledQueueApiMapper(),
                new SeatingFromCalledQueueApiErrorMapper()
            ))
            .build();
    }

    @Test
    void seatsCalledQueueTicketToTableAndMapsRequestToCommand() throws Exception {
        when(applicationService.seatCalledQueueTicket(any())).thenReturn(tableSuccess(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", " idem-seat-queue ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tableId": "%s",
                      "tableGroupId": null,
                      "overrideReasonCode": null,
                      "overrideNote": null,
                      "note": " Seat near window "
                    }
                    """.formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(12))
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-QSEAT-0991"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.seatingId").value(SEATING_ID.toString()))
            .andExpect(jsonPath("$.seatingStatus").value("occupied"))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.resourceId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.alreadySeated").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.seated"))
            .andExpect(jsonPath("$.events[1]").value("reservation.seated"))
            .andExpect(jsonPath("$.events[2]").value("seating.created"))
            .andExpect(jsonPath("$.events[3]").value("table.occupied"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<SeatCalledQueueTicketCommand> commandCaptor = ArgumentCaptor.forClass(SeatCalledQueueTicketCommand.class);
        verify(applicationService).seatCalledQueueTicket(commandCaptor.capture());
        SeatCalledQueueTicketCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.queueTicketId()).isEqualTo(QUEUE_TICKET_ID);
        assertThat(command.tableId()).isEqualTo(TABLE_ID);
        assertThat(command.tableGroupId()).isNull();
        assertThat(command.idempotencyKey()).isEqualTo("idem-seat-queue");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.overrideReasonCode()).isNull();
        assertThat(command.overrideNote()).isNull();
        assertThat(command.note()).isEqualTo("Seat near window");
    }

    @Test
    void seatsCalledQueueTicketToTableGroupAndMapsOverrideFields() throws Exception {
        when(applicationService.seatCalledQueueTicket(any())).thenReturn(tableGroupSuccess());

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-seat-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tableId": null,
                      "tableGroupId": "%s",
                      "overrideReasonCode": " MANUAL_ASSIGNMENT ",
                      "overrideNote": " Large party ",
                      "note": " Birthday group "
                    }
                    """.formatted(TABLE_GROUP_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resourceType").value("table_group"))
            .andExpect(jsonPath("$.resourceId").value(TABLE_GROUP_ID.toString()))
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"));

        ArgumentCaptor<SeatCalledQueueTicketCommand> commandCaptor = ArgumentCaptor.forClass(SeatCalledQueueTicketCommand.class);
        verify(applicationService).seatCalledQueueTicket(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tableId()).isNull();
        assertThat(commandCaptor.getValue().tableGroupId()).isEqualTo(TABLE_GROUP_ID);
        assertThat(commandCaptor.getValue().overrideReasonCode()).isEqualTo("MANUAL_ASSIGNMENT");
        assertThat(commandCaptor.getValue().overrideNote()).isEqualTo("Large party");
        assertThat(commandCaptor.getValue().note()).isEqualTo("Birthday group");
    }

    @Test
    void alreadySeatedReturnsOkWithoutEvents() throws Exception {
        when(applicationService.seatCalledQueueTicket(any())).thenReturn(SeatingFromCalledQueueResult.alreadySeated(
            QUEUE_TICKET_ID,
            12,
            RESERVATION_ID,
            "R-QSEAT-0991",
            SEATING_ID,
            "dining_table",
            TABLE_ID,
            4,
            "occupied",
            "active",
            "occupied",
            List.of(),
            List.of(TABLE_ID),
            "completed"
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-already-seated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("seated"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.alreadySeated").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.seatCalledQueueTicket(any())).thenReturn(tableSuccess(true));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("queue.seat.missing_idempotency_key"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void resourceSelectionValidationStopsBeforeService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-both")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s", "tableGroupId": "%s"}
                    """.formatted(TABLE_ID, TABLE_GROUP_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_CONFLICT"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-neither")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_REQUIRED"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void requestDtoOnlyExposesAllowedFields() {
        assertThat(SeatCalledQueueTicketRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("tableId", "tableGroupId", "overrideReasonCode", "overrideNote", "note");
    }

    @Test
    void endpointDeclaresAppGateReservationQueueSeatPermission() throws Exception {
        Method method = SeatingFromCalledQueueController.class.getMethod(
            "seatCalledQueueTicket",
            UUID.class,
            UUID.class,
            String.class,
            SeatCalledQueueTicketRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("queue.seat");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(SeatingFromCalledQueueError.INVALID_COMMAND, 400, "INVALID_COMMAND", "failed");
        assertApplicationError(SeatingFromCalledQueueError.MISSING_IDEMPOTENCY_KEY, 400, "MISSING_IDEMPOTENCY_KEY", "failed");
        assertApplicationError(SeatingFromCalledQueueError.RESOURCE_SELECTION_CONFLICT, 400, "RESOURCE_SELECTION_CONFLICT", "failed");
        assertApplicationError(SeatingFromCalledQueueError.RESOURCE_SELECTION_REQUIRED, 400, "RESOURCE_SELECTION_REQUIRED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND", "failed");
        assertApplicationError(SeatingFromCalledQueueError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH", "failed");
        assertApplicationError(SeatingFromCalledQueueError.STORE_ACCESS_DENIED, 403, "FORBIDDEN", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_NOT_FOUND, 404, "QUEUE_TICKET_NOT_FOUND", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_STATUS_NOT_CALLED, 409, "QUEUE_TICKET_STATUS_NOT_CALLED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_SOURCE_NOT_RESERVATION, 409, "QUEUE_TICKET_SOURCE_NOT_RESERVATION", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE, 409, "QUEUE_TICKET_CALL_EVIDENCE_INCOMPLETE", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING, 409, "QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_SEATING", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE, 409, "QUEUE_TICKET_SEATED_WITHOUT_ACTIVE_RESOURCE", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED, 409, "QUEUE_TICKET_SEATED_WITH_RESERVATION_NOT_SEATED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_CANNOT_SEAT_CANCELLED, 409, "QUEUE_TICKET_CANNOT_SEAT_CANCELLED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.QUEUE_TICKET_CANNOT_SEAT_EXPIRED, 409, "QUEUE_TICKET_CANNOT_SEAT_EXPIRED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.RESERVATION_NOT_FOUND, 404, "RESERVATION_NOT_FOUND", "failed");
        assertApplicationError(SeatingFromCalledQueueError.RESERVATION_STATUS_NOT_ARRIVED, 409, "RESERVATION_STATUS_NOT_ARRIVED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED, 409, "RESERVATION_SEATED_WITHOUT_QUEUE_TICKET_SEATED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_NOT_FOUND, 404, "TABLE_NOT_FOUND", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_NOT_AVAILABLE, 409, "TABLE_NOT_AVAILABLE", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_CAPACITY_INSUFFICIENT, 409, "TABLE_CAPACITY_INSUFFICIENT", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_LOCK_CONFLICT, 409, "TABLE_LOCK_CONFLICT", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_GROUP_NOT_FOUND, 404, "TABLE_GROUP_NOT_FOUND", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_GROUP_INVALID, 409, "TABLE_GROUP_INVALID", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_GROUP_MEMBER_UNAVAILABLE, 409, "TABLE_GROUP_MEMBER_UNAVAILABLE", "failed");
        assertApplicationError(SeatingFromCalledQueueError.TABLE_GROUP_CAPACITY_INSUFFICIENT, 409, "TABLE_GROUP_CAPACITY_INSUFFICIENT", "failed");
        assertApplicationError(SeatingFromCalledQueueError.IDEMPOTENCY_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS", "started");
        assertApplicationError(SeatingFromCalledQueueError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertApplicationError(SeatingFromCalledQueueError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT", "conflict");
        assertApplicationError(SeatingFromCalledQueueError.ILLEGAL_STATE_TRANSITION, 409, "ILLEGAL_STATE_TRANSITION", "failed");
        assertApplicationError(SeatingFromCalledQueueError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED", "failed");
        assertApplicationError(SeatingFromCalledQueueError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR", "failed");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("queue.seat"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingQueueSeatPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    private void assertApplicationError(
        SeatingFromCalledQueueError applicationError,
        int expectedStatus,
        String expectedApiCode,
        String expectedIdempotencyStatus
    ) throws Exception {
        SeatingFromCalledQueueResult result = applicationError == SeatingFromCalledQueueError.IDEMPOTENCY_IN_PROGRESS
            ? SeatingFromCalledQueueResult.retryLater(applicationError)
            : SeatingFromCalledQueueResult.failure(applicationError);
        when(applicationService.seatCalledQueueTicket(any())).thenReturn(result);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(tableBody(TABLE_ID)))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value(messageKey(expectedApiCode)))
            .andExpect(jsonPath("$.error.details").isMap())
            .andExpect(jsonPath("$.idempotency.status").value(expectedIdempotencyStatus));
    }

    private static SeatingFromCalledQueueResult tableSuccess(boolean replayed) {
        if (replayed) {
            return SeatingFromCalledQueueResult.replay(
                QUEUE_TICKET_ID,
                12,
                "seated",
                RESERVATION_ID,
                "R-QSEAT-0991",
                "seated",
                SEATING_ID,
                "dining_table",
                TABLE_ID,
                4,
                "occupied",
                "active",
                "occupied",
                List.of(),
                false
            );
        }
        return SeatingFromCalledQueueResult.success(
            QUEUE_TICKET_ID,
            12,
            RESERVATION_ID,
            "R-QSEAT-0991",
            SEATING_ID,
            "dining_table",
            TABLE_ID,
            4,
            "occupied",
            List.of(),
            List.of(TABLE_ID),
            "completed",
            List.of("queue_ticket.seated", "reservation.seated", "seating.created", "table.occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static SeatingFromCalledQueueResult tableGroupSuccess() {
        return SeatingFromCalledQueueResult.success(
            QUEUE_TICKET_ID,
            12,
            RESERVATION_ID,
            "R-QSEAT-0991",
            SEATING_ID,
            "table_group",
            TABLE_GROUP_ID,
            4,
            null,
            List.of("occupied", "occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            "completed",
            List.of("queue_ticket.seated", "reservation.seated", "seating.created", "table.occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String tableBody(UUID tableId) {
        return """
            {"tableId": "%s"}
            """.formatted(tableId);
    }

    private static String messageKey(String apiCode) {
        return "queue.seat." + apiCode.toLowerCase();
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
