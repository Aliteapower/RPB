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
import com.rpb.reservation.reservation.application.ReservationArrivedToQueueError;
import com.rpb.reservation.reservation.application.ReservationArrivedToQueueResult;
import com.rpb.reservation.reservation.application.command.QueueArrivedReservationCommand;
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
import java.time.LocalDate;
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

class ReservationArrivedToQueueControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/queue";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000971");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000979");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000971");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000971");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000971");
    private static final UUID QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000971");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");

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
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("reservation.queue"), Set.of(STORE_ID)));
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
    void queuesArrivedReservationAndMapsRequestToCommand() throws Exception {
        when(queueApplicationService.queueArrivedReservation(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", " idem-queue ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySizeGroup": " 3-4 ",
                      "reasonCode": " NO_TABLE_AVAILABLE ",
                      "note": " Customer is waiting near entrance "
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-QUEUE-0971"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(7))
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.queueGroupId").value(QUEUE_GROUP_ID.toString()))
            .andExpect(jsonPath("$.partySize").value(4))
            .andExpect(jsonPath("$.partySizeGroup").value("3-4"))
            .andExpect(jsonPath("$.businessDate").value("2030-06-20"))
            .andExpect(jsonPath("$.queuePosition").value(2))
            .andExpect(jsonPath("$.alreadyQueued").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.queued"))
            .andExpect(jsonPath("$.events[1]").value("queue_ticket.created"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<QueueArrivedReservationCommand> commandCaptor = ArgumentCaptor.forClass(QueueArrivedReservationCommand.class);
        verify(queueApplicationService).queueArrivedReservation(commandCaptor.capture());
        QueueArrivedReservationCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-queue");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.partySizeGroup()).isEqualTo("3-4");
        assertThat(command.reasonCode()).isEqualTo("NO_TABLE_AVAILABLE");
        assertThat(command.note()).isEqualTo("Customer is waiting near entrance");
        verifyNoInteractions(createApplicationService);
        verifyNoInteractions(checkInApplicationService);
        verifyNoInteractions(seatingApplicationService);
    }

    @Test
    void alreadyQueuedWithNewKeyReturnsOkWithoutEvents() throws Exception {
        when(queueApplicationService.queueArrivedReservation(any())).thenReturn(ReservationArrivedToQueueResult.alreadyQueued(
            RESERVATION_ID,
            "R-QUEUE-0971",
            QUEUE_TICKET_ID,
            7,
            QUEUE_GROUP_ID,
            "3-4",
            4,
            "3-4",
            BUSINESS_DATE,
            2,
            "completed"
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-already-queued")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.alreadyQueued").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(queueApplicationService.queueArrivedReservation(any())).thenReturn(success(true));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
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

        verifyNoInteractions(queueApplicationService);
    }

    @Test
    void requestDtoOnlyExposesAllowedFields() {
        assertThat(QueueArrivedReservationRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("partySizeGroup", "reasonCode", "note");
    }

    @Test
    void endpointDeclaresAppGateReservationQueuePermission() throws Exception {
        Method method = ReservationController.class.getMethod(
            "queueArrivedReservation",
            UUID.class,
            UUID.class,
            String.class,
            QueueArrivedReservationRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("reservation.queue");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(ReservationArrivedToQueueError.INVALID_COMMAND, 400, "INVALID_COMMAND", "failed");
        assertApplicationError(ReservationArrivedToQueueError.MISSING_IDEMPOTENCY_KEY, 400, "MISSING_IDEMPOTENCY_KEY", "failed");
        assertApplicationError(ReservationArrivedToQueueError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND", "failed");
        assertApplicationError(ReservationArrivedToQueueError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH", "failed");
        assertApplicationError(ReservationArrivedToQueueError.STORE_ACCESS_DENIED, 403, "FORBIDDEN", "failed");
        assertApplicationError(ReservationArrivedToQueueError.RESERVATION_NOT_FOUND, 404, "RESERVATION_NOT_FOUND", "failed");
        assertApplicationError(ReservationArrivedToQueueError.RESERVATION_STATUS_NOT_ARRIVED, 409, "RESERVATION_STATUS_NOT_ARRIVED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_SEATED, 409, "RESERVATION_CANNOT_QUEUE_SEATED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_CANCELLED, 409, "RESERVATION_CANNOT_QUEUE_CANCELLED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_NO_SHOW, 409, "RESERVATION_CANNOT_QUEUE_NO_SHOW", "failed");
        assertApplicationError(ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_COMPLETED, 409, "RESERVATION_CANNOT_QUEUE_COMPLETED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.QUEUE_GROUP_NOT_FOUND, 404, "QUEUE_GROUP_NOT_FOUND", "failed");
        assertApplicationError(ReservationArrivedToQueueError.QUEUE_GROUP_CANNOT_BE_DERIVED, 409, "QUEUE_GROUP_CANNOT_BE_DERIVED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.QUEUE_GROUP_PARTY_SIZE_MISMATCH, 409, "QUEUE_GROUP_PARTY_SIZE_MISMATCH", "failed");
        assertApplicationError(ReservationArrivedToQueueError.QUEUE_TICKET_NUMBER_CONFLICT, 409, "QUEUE_TICKET_NUMBER_CONFLICT", "failed");
        assertApplicationError(ReservationArrivedToQueueError.ACTIVE_QUEUE_TICKET_CONFLICT, 409, "ACTIVE_QUEUE_TICKET_CONFLICT", "failed");
        assertApplicationError(ReservationArrivedToQueueError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS", "started");
        assertApplicationError(ReservationArrivedToQueueError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertApplicationError(ReservationArrivedToQueueError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT", "conflict");
        assertApplicationError(ReservationArrivedToQueueError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED", "failed");
        assertApplicationError(ReservationArrivedToQueueError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR", "failed");
        assertApplicationError(ReservationArrivedToQueueError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR", "failed");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("reservation.queue"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(queueApplicationService);
    }

    @Test
    void missingQueuePermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("reservation.seat"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(queueApplicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(queueApplicationService);
    }

    private void assertApplicationError(
        ReservationArrivedToQueueError applicationError,
        int expectedStatus,
        String expectedApiCode,
        String expectedIdempotencyStatus
    ) throws Exception {
        ReservationArrivedToQueueResult result = applicationError == ReservationArrivedToQueueError.COMMAND_IN_PROGRESS
            ? ReservationArrivedToQueueResult.retryLater(applicationError)
            : ReservationArrivedToQueueResult.failure(applicationError);
        when(queueApplicationService.queueArrivedReservation(any())).thenReturn(result);

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

    private static ReservationArrivedToQueueResult success(boolean replayed) {
        if (replayed) {
            return ReservationArrivedToQueueResult.replay(
                RESERVATION_ID,
                "R-QUEUE-0971",
                QUEUE_TICKET_ID,
                7,
                "waiting",
                QUEUE_GROUP_ID,
                "3-4",
                4,
                "3-4",
                BUSINESS_DATE,
                2,
                false
            );
        }
        return ReservationArrivedToQueueResult.success(
            RESERVATION_ID,
            "R-QUEUE-0971",
            QUEUE_TICKET_ID,
            7,
            QUEUE_GROUP_ID,
            "3-4",
            4,
            "3-4",
            BUSINESS_DATE,
            2,
            "completed",
            List.of("reservation.queued", "queue_ticket.created"),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String validRequestJson() {
        return """
            {
              "partySizeGroup": "3-4",
              "reasonCode": "NO_TABLE_AVAILABLE",
              "note": "Customer is waiting near entrance"
            }
            """;
    }

    private static String messageKey(String apiCode) {
        return switch (apiCode) {
            case "EVENT_WRITE_FAILED" -> "reservation.event_write_failed";
            case "RESERVATION_NOT_FOUND" -> "reservation.not_found";
            case "RESERVATION_STATUS_NOT_ARRIVED" -> "reservation.status_not_arrived";
            case "RESERVATION_CANNOT_QUEUE_SEATED" -> "reservation.cannot_queue_seated";
            case "RESERVATION_CANNOT_QUEUE_CANCELLED" -> "reservation.cannot_queue_cancelled";
            case "RESERVATION_CANNOT_QUEUE_NO_SHOW" -> "reservation.cannot_queue_no_show";
            case "RESERVATION_CANNOT_QUEUE_COMPLETED" -> "reservation.cannot_queue_completed";
            case "QUEUE_GROUP_NOT_FOUND" -> "reservation.queue_group_not_found";
            case "QUEUE_GROUP_CANNOT_BE_DERIVED" -> "reservation.queue_group_cannot_be_derived";
            case "QUEUE_GROUP_PARTY_SIZE_MISMATCH" -> "reservation.queue_group_party_size_mismatch";
            case "QUEUE_TICKET_NUMBER_CONFLICT" -> "reservation.queue_ticket_number_conflict";
            case "ACTIVE_QUEUE_TICKET_CONFLICT" -> "reservation.active_queue_ticket_conflict";
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
