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
import com.rpb.reservation.reservation.application.ReservationCancelError;
import com.rpb.reservation.reservation.application.ReservationCancelResult;
import com.rpb.reservation.reservation.application.command.CancelReservationCommand;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCancelApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.time.Instant;
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

class ReservationCancelControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/cancel";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000989");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000981");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000981");
    private static final Instant CANCELLED_AT = Instant.parse("2030-06-20T03:20:00Z");

    private ReservationCreateApplicationService createApplicationService;
    private ReservationCheckInApplicationService checkInApplicationService;
    private ReservationArrivedDirectSeatingApplicationService seatingApplicationService;
    private ReservationArrivedToQueueApplicationService queueApplicationService;
    private ReservationCancelApplicationService cancelApplicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createApplicationService = mock(ReservationCreateApplicationService.class);
        checkInApplicationService = mock(ReservationCheckInApplicationService.class);
        seatingApplicationService = mock(ReservationArrivedDirectSeatingApplicationService.class);
        queueApplicationService = mock(ReservationArrivedToQueueApplicationService.class);
        cancelApplicationService = mock(ReservationCancelApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("reservation.cancel"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReservationController(
                createApplicationService,
                checkInApplicationService,
                seatingApplicationService,
                queueApplicationService,
                cancelApplicationService,
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
                new ReservationCancelApiErrorMapper()
            ))
            .build();
    }

    @Test
    void cancelsReservationAndMapsRequestToCommand() throws Exception {
        when(cancelApplicationService.cancelReservation(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", " idem-cancel ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cancelledAt": "2030-06-20T03:20:00Z",
                      "reasonCode": " guest_requested ",
                      "note": " Customer called to cancel "
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-CANCEL-0981"))
            .andExpect(jsonPath("$.status").value("cancelled"))
            .andExpect(jsonPath("$.cancelledAt").value("2030-06-20T03:20:00Z"))
            .andExpect(jsonPath("$.cancellationReasonCode").value("guest_requested"))
            .andExpect(jsonPath("$.alreadyCancelled").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.cancelled"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<CancelReservationCommand> commandCaptor = ArgumentCaptor.forClass(CancelReservationCommand.class);
        verify(cancelApplicationService).cancelReservation(commandCaptor.capture());
        CancelReservationCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-cancel");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.cancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(command.reasonCode()).isEqualTo("guest_requested");
        assertThat(command.note()).isEqualTo("Customer called to cancel");
        verifyNoInteractions(createApplicationService, checkInApplicationService, seatingApplicationService, queueApplicationService);
    }

    @Test
    void alreadyCancelledWithNewKeyReturnsSuccessLikeResponseWithoutEvents() throws Exception {
        when(cancelApplicationService.cancelReservation(any())).thenReturn(ReservationCancelResult.alreadyCancelled(
            RESERVATION_ID,
            "R-CANCEL-0981",
            CANCELLED_AT,
            "guest_requested",
            "completed"
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-already-cancelled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("cancelled"))
            .andExpect(jsonPath("$.alreadyCancelled").value(true))
            .andExpect(jsonPath("$.cancelledAt").value("2030-06-20T03:20:00Z"))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(cancelApplicationService.cancelReservation(any())).thenReturn(ReservationCancelResult.replay(
            RESERVATION_ID,
            "R-CANCEL-0981",
            "cancelled",
            CANCELLED_AT,
            "guest_requested",
            false
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
            .andExpect(jsonPath("$.alreadyCancelled").value(false))
            .andExpect(jsonPath("$.events").isEmpty());
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.missing_idempotency_key"));

        verifyNoInteractions(cancelApplicationService);
    }

    @Test
    void requestDtoOnlyExposesCancelledAtReasonCodeAndNote() {
        assertThat(CancelReservationRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("cancelledAt", "reasonCode", "note");
    }

    @Test
    void cancelEndpointDeclaresAppGateReservationQueueCancelPermission() throws Exception {
        Method method = ReservationController.class.getMethod(
            "cancelReservation",
            UUID.class,
            UUID.class,
            String.class,
            CancelReservationRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.cancel");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(ReservationCancelError.INVALID_COMMAND, 400, "INVALID_COMMAND", "failed");
        assertApplicationError(ReservationCancelError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND", "failed");
        assertApplicationError(ReservationCancelError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH", "failed");
        assertApplicationError(ReservationCancelError.STORE_ACCESS_DENIED, 403, "FORBIDDEN", "failed");
        assertApplicationError(ReservationCancelError.RESERVATION_NOT_FOUND, 404, "RESERVATION_NOT_FOUND", "failed");
        assertApplicationError(ReservationCancelError.RESERVATION_CANNOT_CANCEL_ARRIVED, 409, "RESERVATION_CANNOT_CANCEL_ARRIVED", "failed");
        assertApplicationError(ReservationCancelError.RESERVATION_CANNOT_CANCEL_SEATED, 409, "RESERVATION_CANNOT_CANCEL_SEATED", "failed");
        assertApplicationError(ReservationCancelError.RESERVATION_CANNOT_CANCEL_NO_SHOW, 409, "RESERVATION_CANNOT_CANCEL_NO_SHOW", "failed");
        assertApplicationError(ReservationCancelError.RESERVATION_CANNOT_CANCEL_COMPLETED, 409, "RESERVATION_CANNOT_CANCEL_COMPLETED", "failed");
        assertApplicationError(ReservationCancelError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS", "started");
        assertApplicationError(ReservationCancelError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertApplicationError(ReservationCancelError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT", "conflict");
        assertApplicationError(ReservationCancelError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationCancelError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationCancelError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED", "failed");
        assertApplicationError(ReservationCancelError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR", "failed");
        assertApplicationError(ReservationCancelError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR", "failed");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("reservation.cancel"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(cancelApplicationService);
    }

    @Test
    void missingCancelPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of(), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(cancelApplicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(cancelApplicationService);
    }

    private void assertApplicationError(
        ReservationCancelError applicationError,
        int expectedStatus,
        String expectedApiCode,
        String expectedIdempotencyStatus
    ) throws Exception {
        ReservationCancelResult result = applicationError == ReservationCancelError.COMMAND_IN_PROGRESS
            ? ReservationCancelResult.retryLater(applicationError)
            : ReservationCancelResult.failure(applicationError);
        when(cancelApplicationService.cancelReservation(any())).thenReturn(result);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value(messageKey(expectedApiCode)))
            .andExpect(jsonPath("$.error.details").isMap())
            .andExpect(jsonPath("$.idempotency.status").value(expectedIdempotencyStatus));
    }

    private static ReservationCancelResult success(boolean replayed) {
        if (replayed) {
            return ReservationCancelResult.replay(
                RESERVATION_ID,
                "R-CANCEL-0981",
                "cancelled",
                CANCELLED_AT,
                "guest_requested",
                false
            );
        }
        return ReservationCancelResult.success(
            RESERVATION_ID,
            "R-CANCEL-0981",
            "cancelled",
            CANCELLED_AT,
            "guest_requested",
            "completed",
            List.of("reservation.cancelled"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String validRequestJson() {
        return """
            {
              "cancelledAt": "2030-06-20T03:20:00Z",
              "reasonCode": "guest_requested",
              "note": "Customer called to cancel"
            }
            """;
    }

    private static String messageKey(String apiCode) {
        return switch (apiCode) {
            case "EVENT_WRITE_FAILED" -> "reservation.event_write_failed";
            case "RESERVATION_NOT_FOUND" -> "reservation.not_found";
            case "RESERVATION_CANNOT_CANCEL_ARRIVED" -> "reservation.cannot_cancel_arrived";
            case "RESERVATION_CANNOT_CANCEL_SEATED" -> "reservation.cannot_cancel_seated";
            case "RESERVATION_CANNOT_CANCEL_NO_SHOW" -> "reservation.cannot_cancel_no_show";
            case "RESERVATION_CANNOT_CANCEL_COMPLETED" -> "reservation.cannot_cancel_completed";
            default -> "reservation." + apiCode.toLowerCase();
        };
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
