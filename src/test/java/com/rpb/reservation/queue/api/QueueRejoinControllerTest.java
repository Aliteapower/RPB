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
import com.rpb.reservation.queue.application.QueueRejoinError;
import com.rpb.reservation.queue.application.QueueRejoinResult;
import com.rpb.reservation.queue.application.command.RejoinQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueRejoinApplicationService;
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

class QueueRejoinControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/rejoin";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000986");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000986");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000989");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000986");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000986");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000986");
    private static final Instant REJOINED_AT = Instant.parse("2030-06-20T03:55:00Z");

    private QueueRejoinApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(QueueRejoinApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("queue.rejoin"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new QueueRejoinController(
                applicationService,
                actorProvider,
                new QueueRejoinApiMapper(),
                new QueueRejoinApiErrorMapper()
            ))
            .build();
    }

    @Test
    void rejoinsSkippedQueueTicketAndMapsRequestToCommand() throws Exception {
        when(applicationService.rejoinQueueTicket(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", " idem-rejoin ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "note": " Customer returned "
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(12))
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.queuePosition").value(42))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-REJOIN-0986"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.rejoinedAt").value(REJOINED_AT.toString()))
            .andExpect(jsonPath("$.alreadyRejoined").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.rejoined"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<RejoinQueueTicketCommand> commandCaptor = ArgumentCaptor.forClass(RejoinQueueTicketCommand.class);
        verify(applicationService).rejoinQueueTicket(commandCaptor.capture());
        RejoinQueueTicketCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.queueTicketId()).isEqualTo(QUEUE_TICKET_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-rejoin");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.note()).isEqualTo("Customer returned");
    }

    @Test
    void nullBodyUsesOnlyPathHeaderAndActorScope() throws Exception {
        when(applicationService.rejoinQueueTicket(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-null-body")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        ArgumentCaptor<RejoinQueueTicketCommand> commandCaptor = ArgumentCaptor.forClass(RejoinQueueTicketCommand.class);
        verify(applicationService).rejoinQueueTicket(commandCaptor.capture());
        assertThat(commandCaptor.getValue().note()).isNull();
    }

    @Test
    void alreadyRejoinedReturnsOkWithoutEvents() throws Exception {
        when(applicationService.rejoinQueueTicket(any())).thenReturn(QueueRejoinResult.alreadyRejoined(
            QUEUE_TICKET_ID,
            12,
            42,
            RESERVATION_ID,
            "R-REJOIN-0986",
            REJOINED_AT,
            "completed"
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-already-rejoined")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.queuePosition").value(42))
            .andExpect(jsonPath("$.alreadyRejoined").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.rejoinQueueTicket(any())).thenReturn(success(true));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("queue.rejoin.missing_idempotency_key"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void requestDtoOnlyExposesAllowedFields() {
        assertThat(RejoinQueueTicketRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("note");
    }

    @Test
    void endpointDeclaresAppGateReservationQueueRejoinPermission() throws Exception {
        Method method = QueueRejoinController.class.getMethod(
            "rejoinQueueTicket",
            UUID.class,
            UUID.class,
            String.class,
            RejoinQueueTicketRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("queue.rejoin");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(QueueRejoinError.INVALID_COMMAND, 400, "INVALID_COMMAND", "failed");
        assertApplicationError(QueueRejoinError.MISSING_IDEMPOTENCY_KEY, 400, "MISSING_IDEMPOTENCY_KEY", "failed");
        assertApplicationError(QueueRejoinError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND", "failed");
        assertApplicationError(QueueRejoinError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH", "failed");
        assertApplicationError(QueueRejoinError.STORE_ACCESS_DENIED, 403, "FORBIDDEN", "failed");
        assertApplicationError(QueueRejoinError.QUEUE_TICKET_NOT_FOUND, 404, "QUEUE_TICKET_NOT_FOUND", "failed");
        assertApplicationError(QueueRejoinError.QUEUE_TICKET_STATUS_NOT_SKIPPED, 409, "QUEUE_TICKET_STATUS_NOT_SKIPPED", "failed");
        assertApplicationError(QueueRejoinError.QUEUE_REJOIN_EVIDENCE_INCOMPLETE, 409, "QUEUE_REJOIN_EVIDENCE_INCOMPLETE", "failed");
        assertApplicationError(QueueRejoinError.RESERVATION_NOT_FOUND, 404, "RESERVATION_NOT_FOUND", "failed");
        assertApplicationError(QueueRejoinError.RESERVATION_STATUS_NOT_ARRIVED, 409, "RESERVATION_STATUS_NOT_ARRIVED", "failed");
        assertApplicationError(QueueRejoinError.IDEMPOTENCY_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS", "started");
        assertApplicationError(QueueRejoinError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertApplicationError(QueueRejoinError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT", "conflict");
        assertApplicationError(QueueRejoinError.ILLEGAL_STATE_TRANSITION, 409, "ILLEGAL_STATE_TRANSITION", "failed");
        assertApplicationError(QueueRejoinError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED", "failed");
        assertApplicationError(QueueRejoinError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED", "failed");
        assertApplicationError(QueueRejoinError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED", "failed");
        assertApplicationError(QueueRejoinError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR", "failed");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("queue.rejoin"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingQueueRejoinPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("queue.skip"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    private void assertApplicationError(
        QueueRejoinError applicationError,
        int expectedStatus,
        String expectedApiCode,
        String expectedIdempotencyStatus
    ) throws Exception {
        QueueRejoinResult result = applicationError == QueueRejoinError.IDEMPOTENCY_IN_PROGRESS
            ? QueueRejoinResult.retryLater(applicationError)
            : QueueRejoinResult.failure(applicationError);
        when(applicationService.rejoinQueueTicket(any())).thenReturn(result);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
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

    private static QueueRejoinResult success(boolean replayed) {
        if (replayed) {
            return QueueRejoinResult.replay(
                QUEUE_TICKET_ID,
                12,
                "waiting",
                42,
                RESERVATION_ID,
                "R-REJOIN-0986",
                "arrived",
                REJOINED_AT,
                false
            );
        }
        return QueueRejoinResult.success(
            QUEUE_TICKET_ID,
            12,
            42,
            RESERVATION_ID,
            "R-REJOIN-0986",
            REJOINED_AT,
            "completed",
            List.of("queue_ticket.rejoined"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String validRequestJson() {
        return """
            {
              "note": "Customer returned"
            }
            """;
    }

    private static String messageKey(String apiCode) {
        return "queue.rejoin." + apiCode.toLowerCase();
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
