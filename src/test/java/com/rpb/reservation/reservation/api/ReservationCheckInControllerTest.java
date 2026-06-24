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
import com.rpb.reservation.reservation.application.ReservationCheckInError;
import com.rpb.reservation.reservation.application.ReservationCheckInResult;
import com.rpb.reservation.reservation.application.command.CheckInReservationCommand;
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

class ReservationCheckInControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/check-in";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000701");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000701");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000799");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000701");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000701");
    private static final Instant ARRIVED_AT = Instant.parse("2030-06-20T03:10:00Z");

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
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("reservation.check_in"), Set.of(STORE_ID)));
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
    void checksInConfirmedReservationAndMapsRequestToCommand() throws Exception {
        when(checkInApplicationService.checkInReservation(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", " idem-check-in ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "arrivedAt": "2030-06-20T03:10:00Z",
                      "reasonCode": " customer_arrived ",
                      "note": " Guest is waiting at host stand "
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-20300620-0701"))
            .andExpect(jsonPath("$.status").value("arrived"))
            .andExpect(jsonPath("$.arrivedAt").value("2030-06-20T03:10:00Z"))
            .andExpect(jsonPath("$.alreadyArrived").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.arrived"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<CheckInReservationCommand> commandCaptor = ArgumentCaptor.forClass(CheckInReservationCommand.class);
        verify(checkInApplicationService).checkInReservation(commandCaptor.capture());
        CheckInReservationCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-check-in");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.arrivedAt()).isEqualTo(ARRIVED_AT);
        assertThat(command.reasonCode()).isEqualTo("customer_arrived");
        assertThat(command.note()).isEqualTo("Guest is waiting at host stand");
        verifyNoInteractions(createApplicationService);
    }

    @Test
    void alreadyArrivedWithNewKeyReturnsSuccessLikeResponseWithoutEvents() throws Exception {
        when(checkInApplicationService.checkInReservation(any())).thenReturn(ReservationCheckInResult.alreadyArrived(
            RESERVATION_ID,
            "R-20300620-0701",
            ARRIVED_AT,
            "completed"
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-already-arrived")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("arrived"))
            .andExpect(jsonPath("$.alreadyArrived").value(true))
            .andExpect(jsonPath("$.arrivedAt").value("2030-06-20T03:10:00Z"))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(checkInApplicationService.checkInReservation(any())).thenReturn(ReservationCheckInResult.replay(
            RESERVATION_ID,
            "R-20300620-0701",
            "arrived",
            ARRIVED_AT,
            false
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
            .andExpect(jsonPath("$.alreadyArrived").value(false))
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

        verifyNoInteractions(checkInApplicationService);
        verifyNoInteractions(createApplicationService);
    }

    @Test
    void requestDtoOnlyExposesArrivedAtReasonCodeAndNote() {
        assertThat(CheckInReservationRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("arrivedAt", "reasonCode", "note");
    }

    @Test
    void checkInEndpointDeclaresAppGateReservationQueuePermission() throws Exception {
        Method method = ReservationController.class.getMethod(
            "checkInReservation",
            UUID.class,
            UUID.class,
            String.class,
            CheckInReservationRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.check_in");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(ReservationCheckInError.INVALID_COMMAND, 400, "INVALID_COMMAND", "failed");
        assertApplicationError(ReservationCheckInError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND", "failed");
        assertApplicationError(ReservationCheckInError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH", "failed");
        assertApplicationError(ReservationCheckInError.STORE_ACCESS_DENIED, 403, "FORBIDDEN", "failed");
        assertApplicationError(ReservationCheckInError.RESERVATION_NOT_FOUND, 404, "RESERVATION_NOT_FOUND", "failed");
        assertApplicationError(ReservationCheckInError.RESERVATION_STATUS_NOT_CONFIRMED, 409, "RESERVATION_STATUS_NOT_CONFIRMED", "failed");
        assertApplicationError(ReservationCheckInError.RESERVATION_NOT_TODAY, 409, "RESERVATION_NOT_TODAY", "failed");
        assertApplicationError(ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_CANCELLED, 409, "RESERVATION_CANNOT_CHECK_IN_CANCELLED", "failed");
        assertApplicationError(ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_NO_SHOW, 409, "RESERVATION_CANNOT_CHECK_IN_NO_SHOW", "failed");
        assertApplicationError(ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_COMPLETED, 409, "RESERVATION_CANNOT_CHECK_IN_COMPLETED", "failed");
        assertApplicationError(ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_SEATED, 409, "RESERVATION_CANNOT_CHECK_IN_SEATED", "failed");
        assertApplicationError(ReservationCheckInError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS", "started");
        assertApplicationError(ReservationCheckInError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertApplicationError(ReservationCheckInError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT", "conflict");
        assertApplicationError(ReservationCheckInError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationCheckInError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationCheckInError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED", "failed");
        assertApplicationError(ReservationCheckInError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR", "failed");
        assertApplicationError(ReservationCheckInError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR", "failed");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("reservation.check_in"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(checkInApplicationService);
    }

    @Test
    void missingCheckInPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of(), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(checkInApplicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(checkInApplicationService);
    }

    private void assertApplicationError(
        ReservationCheckInError applicationError,
        int expectedStatus,
        String expectedApiCode,
        String expectedIdempotencyStatus
    ) throws Exception {
        ReservationCheckInResult result = applicationError == ReservationCheckInError.COMMAND_IN_PROGRESS
            ? ReservationCheckInResult.retryLater(applicationError)
            : ReservationCheckInResult.failure(applicationError);
        when(checkInApplicationService.checkInReservation(any())).thenReturn(result);

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

    private static ReservationCheckInResult success(boolean replayed) {
        if (replayed) {
            return ReservationCheckInResult.replay(RESERVATION_ID, "R-20300620-0701", "arrived", ARRIVED_AT, false);
        }
        return ReservationCheckInResult.success(
            RESERVATION_ID,
            "R-20300620-0701",
            "arrived",
            ARRIVED_AT,
            "completed",
            List.of("reservation.arrived"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String validRequestJson() {
        return """
            {
              "arrivedAt": "2030-06-20T03:10:00Z",
              "reasonCode": "customer_arrived",
              "note": "Guest is waiting at host stand"
            }
            """;
    }

    private static String messageKey(String apiCode) {
        return switch (apiCode) {
            case "EVENT_WRITE_FAILED" -> "reservation.event_write_failed";
            case "RESERVATION_NOT_FOUND" -> "reservation.not_found";
            case "RESERVATION_STATUS_NOT_CONFIRMED" -> "reservation.status_not_confirmed";
            case "RESERVATION_NOT_TODAY" -> "reservation.not_today";
            case "RESERVATION_CANNOT_CHECK_IN_CANCELLED" -> "reservation.cannot_check_in_cancelled";
            case "RESERVATION_CANNOT_CHECK_IN_NO_SHOW" -> "reservation.cannot_check_in_no_show";
            case "RESERVATION_CANNOT_CHECK_IN_COMPLETED" -> "reservation.cannot_check_in_completed";
            case "RESERVATION_CANNOT_CHECK_IN_SEATED" -> "reservation.cannot_check_in_seated";
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
