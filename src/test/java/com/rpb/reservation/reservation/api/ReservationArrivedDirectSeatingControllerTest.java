package com.rpb.reservation.reservation.api;

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
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingError;
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingResult;
import com.rpb.reservation.reservation.application.command.SeatArrivedReservationCommand;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCancelApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCompleteApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationNoShowApplicationService;
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

class ReservationArrivedDirectSeatingControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000901");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000901");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000999");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000901");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000901");
    private static final UUID TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000901");
    private static final UUID TABLE_GROUP_ID = UUID.fromString("70000000-0000-0000-0000-000000000901");
    private static final UUID TEMP_TABLE_ID_A = UUID.fromString("70000000-0000-0000-0000-000000000981");
    private static final UUID TEMP_TABLE_ID_B = UUID.fromString("70000000-0000-0000-0000-000000000982");
    private static final UUID SEATING_ID = UUID.fromString("80000000-0000-0000-0000-000000000901");

    private ReservationCreateApplicationService createApplicationService;
    private ReservationCheckInApplicationService checkInApplicationService;
    private ReservationArrivedDirectSeatingApplicationService seatingApplicationService;
    private ReservationArrivedToQueueApplicationService queueApplicationService;
    private ReservationCancelApplicationService cancelApplicationService;
    private ReservationNoShowApplicationService noShowApplicationService;
    private ReservationCompleteApplicationService completeApplicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createApplicationService = mock(ReservationCreateApplicationService.class);
        checkInApplicationService = mock(ReservationCheckInApplicationService.class);
        seatingApplicationService = mock(ReservationArrivedDirectSeatingApplicationService.class);
        queueApplicationService = mock(ReservationArrivedToQueueApplicationService.class);
        cancelApplicationService = mock(ReservationCancelApplicationService.class);
        noShowApplicationService = mock(ReservationNoShowApplicationService.class);
        completeApplicationService = mock(ReservationCompleteApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("reservation.seat"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReservationController(
                createApplicationService,
                checkInApplicationService,
                seatingApplicationService,
                queueApplicationService,
                cancelApplicationService,
                noShowApplicationService,
                completeApplicationService,
                actorProvider,
                new ReservationApiMapper(),
                new ReservationApiErrorMapper(),
                new ReservationCheckInApiMapper(),
                new ReservationCheckInApiErrorMapper(),
                new ReservationArrivedDirectSeatingApiMapper(),
                new ReservationArrivedDirectSeatingApiErrorMapper(),
                new ReservationArrivedToQueueApiMapper(),
                new ReservationArrivedToQueueApiErrorMapper(),
                new ReservationCancelApiMapper(),
                new ReservationCancelApiErrorMapper(),
                new ReservationNoShowApiMapper(),
                new ReservationNoShowApiErrorMapper(),
                new ReservationCompleteApiMapper(),
                new ReservationCompleteApiErrorMapper()
            ))
            .build();
    }

    @Test
    void seatsArrivedReservationToTableAndMapsRequestToCommand() throws Exception {
        when(seatingApplicationService.seatArrivedReservation(any())).thenReturn(tableSuccess(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", " idem-seat ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tableId": "%s",
                      "tableGroupId": null,
                      "overrideReasonCode": null,
                      "overrideNote": null,
                      "note": " Window table "
                    }
                    """.formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-SEAT-0901"))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.seatingId").value(SEATING_ID.toString()))
            .andExpect(jsonPath("$.seatingStatus").value("occupied"))
            .andExpect(jsonPath("$.resourceType").value("table"))
            .andExpect(jsonPath("$.resourceId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.alreadySeated").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.seated"))
            .andExpect(jsonPath("$.events[1]").value("seating.created"))
            .andExpect(jsonPath("$.events[2]").value("table.occupied"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<SeatArrivedReservationCommand> commandCaptor = ArgumentCaptor.forClass(SeatArrivedReservationCommand.class);
        verify(seatingApplicationService).seatArrivedReservation(commandCaptor.capture());
        SeatArrivedReservationCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(command.tableId()).isEqualTo(TABLE_ID);
        assertThat(command.tableGroupId()).isNull();
        assertThat(command.idempotencyKey()).isEqualTo("idem-seat");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.overrideReasonCode()).isNull();
        assertThat(command.overrideNote()).isNull();
        assertThat(command.note()).isEqualTo("Window table");
        verifyNoInteractions(createApplicationService);
        verifyNoInteractions(checkInApplicationService);
    }

    @Test
    void seatsArrivedReservationToTableGroupAndMapsOverrideFields() throws Exception {
        when(seatingApplicationService.seatArrivedReservation(any())).thenReturn(tableGroupSuccess());

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-group")
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
            .andExpect(jsonPath("$.reservationStatus").value("seated"));

        ArgumentCaptor<SeatArrivedReservationCommand> commandCaptor = ArgumentCaptor.forClass(SeatArrivedReservationCommand.class);
        verify(seatingApplicationService).seatArrivedReservation(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tableId()).isNull();
        assertThat(commandCaptor.getValue().tableGroupId()).isEqualTo(TABLE_GROUP_ID);
        assertThat(commandCaptor.getValue().overrideReasonCode()).isEqualTo("MANUAL_ASSIGNMENT");
        assertThat(commandCaptor.getValue().overrideNote()).isEqualTo("Large party");
        assertThat(commandCaptor.getValue().note()).isEqualTo("Birthday group");
    }

    @Test
    void seatsArrivedReservationToTemporaryTablesAndMapsMemberIds() throws Exception {
        when(seatingApplicationService.seatArrivedReservation(any())).thenReturn(tableGroupSuccess());

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-temp-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tableId": null,
                      "tableGroupId": null,
                      "temporaryTableIds": [
                        "%s",
                        "%s"
                      ],
                      "note": " Combine tables "
                    }
                    """.formatted(TEMP_TABLE_ID_A, TEMP_TABLE_ID_B)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resourceType").value("table_group"));

        ArgumentCaptor<SeatArrivedReservationCommand> commandCaptor = ArgumentCaptor.forClass(SeatArrivedReservationCommand.class);
        verify(seatingApplicationService).seatArrivedReservation(commandCaptor.capture());
        SeatArrivedReservationCommand command = commandCaptor.getValue();
        assertThat(command.tableId()).isNull();
        assertThat(command.tableGroupId()).isNull();
        assertThat(command.temporaryTableIds()).containsExactly(TEMP_TABLE_ID_A, TEMP_TABLE_ID_B);
        assertThat(command.note()).isEqualTo("Combine tables");
    }

    @Test
    void alreadySeatedReturnsOkWithoutEvents() throws Exception {
        when(seatingApplicationService.seatArrivedReservation(any())).thenReturn(ReservationArrivedDirectSeatingResult.alreadySeated(
            RESERVATION_ID,
            "R-SEAT-0901",
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

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-already-seated")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.alreadySeated").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(seatingApplicationService.seatArrivedReservation(any())).thenReturn(tableSuccess(true));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
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
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.missing_idempotency_key"));

        verifyNoInteractions(seatingApplicationService);
    }

    @Test
    void resourceSelectionValidationStopsBeforeService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-both")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s", "tableGroupId": "%s"}
                    """.formatted(TABLE_ID, TABLE_GROUP_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_CONFLICT"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-neither")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_REQUIRED"));

        verifyNoInteractions(seatingApplicationService);
    }

    @Test
    void temporaryTableSelectionValidationStopsBeforeService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-empty-temp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"temporaryTableIds": []}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED"));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-one-temp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"temporaryTableIds": ["%s"]}
                    """.formatted(TEMP_TABLE_ID_A)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED"));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-duplicate-temp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"temporaryTableIds": ["%s", "%s"]}
                    """.formatted(TEMP_TABLE_ID_A, TEMP_TABLE_ID_A)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE"));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-conflicting-temp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s", "temporaryTableIds": ["%s", "%s"]}
                    """.formatted(TABLE_ID, TEMP_TABLE_ID_A, TEMP_TABLE_ID_B)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_CONFLICT"));

        verifyNoInteractions(seatingApplicationService);
    }

    @Test
    void requestDtoOnlyExposesAllowedFields() {
        assertThat(SeatArrivedReservationRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("tableId", "tableGroupId", "temporaryTableIds", "overrideReasonCode", "overrideNote", "note");
    }

    @Test
    void endpointDeclaresAppGateReservationSeatPermission() throws Exception {
        Method method = ReservationController.class.getMethod(
            "seatArrivedReservation",
            UUID.class,
            UUID.class,
            String.class,
            SeatArrivedReservationRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.seat");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(ReservationArrivedDirectSeatingError.INVALID_COMMAND, 400, "INVALID_COMMAND", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.MISSING_IDEMPOTENCY_KEY, 400, "MISSING_IDEMPOTENCY_KEY", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESOURCE_SELECTION_CONFLICT, 400, "RESOURCE_SELECTION_CONFLICT", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESOURCE_SELECTION_REQUIRED, 400, "RESOURCE_SELECTION_REQUIRED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.STORE_ACCESS_DENIED, 403, "FORBIDDEN", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESERVATION_NOT_FOUND, 404, "RESERVATION_NOT_FOUND", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESERVATION_STATUS_NOT_ARRIVED, 409, "RESERVATION_STATUS_NOT_ARRIVED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESERVATION_NOT_TODAY, 409, "RESERVATION_NOT_TODAY", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING, 409, "RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_CANCELLED, 409, "RESERVATION_CANNOT_SEAT_CANCELLED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_NO_SHOW, 409, "RESERVATION_CANNOT_SEAT_NO_SHOW", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_COMPLETED, 409, "RESERVATION_CANNOT_SEAT_COMPLETED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_NOT_FOUND, 404, "TABLE_NOT_FOUND", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_NOT_AVAILABLE, 409, "TABLE_NOT_AVAILABLE", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_CAPACITY_INSUFFICIENT, 409, "TABLE_CAPACITY_INSUFFICIENT", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_LOCK_CONFLICT, 409, "TABLE_LOCK_CONFLICT", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_GROUP_NOT_FOUND, 404, "TABLE_GROUP_NOT_FOUND", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_GROUP_INVALID, 409, "TABLE_GROUP_INVALID", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_GROUP_MEMBER_UNAVAILABLE, 409, "TABLE_GROUP_MEMBER_UNAVAILABLE", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TABLE_GROUP_CAPACITY_INSUFFICIENT, 409, "TABLE_GROUP_CAPACITY_INSUFFICIENT", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED, 400, "TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE, 400, "TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE, 409, "TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT, 409, "TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TEMPORARY_TABLE_GROUP_LOCK_CONFLICT, 409, "TEMPORARY_TABLE_GROUP_LOCK_CONFLICT", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT, 409, "TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT", "conflict");
        assertApplicationError(ReservationArrivedDirectSeatingError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS", "started");
        assertApplicationError(ReservationArrivedDirectSeatingError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR", "failed");
        assertApplicationError(ReservationArrivedDirectSeatingError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR", "failed");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("reservation.seat"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(seatingApplicationService);
    }

    @Test
    void missingSeatPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("reservation.check_in"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(seatingApplicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(seatingApplicationService);
    }

    private void assertApplicationError(
        ReservationArrivedDirectSeatingError applicationError,
        int expectedStatus,
        String expectedApiCode,
        String expectedIdempotencyStatus
    ) throws Exception {
        ReservationArrivedDirectSeatingResult result = applicationError == ReservationArrivedDirectSeatingError.COMMAND_IN_PROGRESS
            ? ReservationArrivedDirectSeatingResult.retryLater(applicationError)
            : ReservationArrivedDirectSeatingResult.failure(applicationError);
        when(seatingApplicationService.seatArrivedReservation(any())).thenReturn(result);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tableId": "%s"}
                    """.formatted(TABLE_ID)))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value(messageKey(expectedApiCode)))
            .andExpect(jsonPath("$.error.details").isMap())
            .andExpect(jsonPath("$.idempotency.status").value(expectedIdempotencyStatus));
    }

    private static ReservationArrivedDirectSeatingResult tableSuccess(boolean replayed) {
        if (replayed) {
            return ReservationArrivedDirectSeatingResult.replay(
                RESERVATION_ID,
                "R-SEAT-0901",
                SEATING_ID,
                "dining_table",
                TABLE_ID,
                4,
                "seated",
                "occupied",
                "active",
                "occupied",
                List.of(),
                false
            );
        }
        return ReservationArrivedDirectSeatingResult.success(
            RESERVATION_ID,
            "R-SEAT-0901",
            SEATING_ID,
            "dining_table",
            TABLE_ID,
            4,
            "occupied",
            List.of(),
            List.of(TABLE_ID),
            "completed",
            List.of("reservation.seated", "seating.created", "table.occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static ReservationArrivedDirectSeatingResult tableGroupSuccess() {
        return ReservationArrivedDirectSeatingResult.success(
            RESERVATION_ID,
            "R-SEAT-0901",
            SEATING_ID,
            "table_group",
            TABLE_GROUP_ID,
            4,
            null,
            List.of("occupied", "occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            "completed",
            List.of("reservation.seated", "seating.created", "table.occupied"),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String messageKey(String apiCode) {
        return switch (apiCode) {
            case "RESERVATION_NOT_FOUND" -> "reservation.not_found";
            case "RESERVATION_STATUS_NOT_ARRIVED" -> "reservation.status_not_arrived";
            case "RESERVATION_NOT_TODAY" -> "reservation.not_today";
            case "RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING" -> "reservation.seated_without_active_seating";
            case "RESERVATION_CANNOT_SEAT_CANCELLED" -> "reservation.cannot_seat_cancelled";
            case "RESERVATION_CANNOT_SEAT_NO_SHOW" -> "reservation.cannot_seat_no_show";
            case "RESERVATION_CANNOT_SEAT_COMPLETED" -> "reservation.cannot_seat_completed";
            case "TABLE_NOT_FOUND" -> "reservation.table_not_found";
            case "TABLE_NOT_AVAILABLE" -> "reservation.table_not_available";
            case "TABLE_CAPACITY_INSUFFICIENT" -> "reservation.table_capacity_insufficient";
            case "TABLE_LOCK_CONFLICT" -> "reservation.table_lock_conflict";
            case "TABLE_GROUP_NOT_FOUND" -> "reservation.table_group_not_found";
            case "TABLE_GROUP_INVALID" -> "reservation.table_group_invalid";
            case "TABLE_GROUP_MEMBER_UNAVAILABLE" -> "reservation.table_group_member_unavailable";
            case "TABLE_GROUP_CAPACITY_INSUFFICIENT" -> "reservation.table_group_capacity_insufficient";
            case "RESOURCE_SELECTION_CONFLICT" -> "reservation.resource_selection_conflict";
            case "RESOURCE_SELECTION_REQUIRED" -> "reservation.resource_selection_required";
            case "EVENT_WRITE_FAILED" -> "reservation.event_write_failed";
            default -> "reservation." + expectedKeySuffix(apiCode);
        };
    }

    private static String expectedKeySuffix(String apiCode) {
        return apiCode.toLowerCase();
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
