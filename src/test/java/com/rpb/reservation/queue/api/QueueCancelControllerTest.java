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
import com.rpb.reservation.queue.application.QueueCancelError;
import com.rpb.reservation.queue.application.QueueCancelResult;
import com.rpb.reservation.queue.application.command.CancelQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueCancelApplicationService;
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

class QueueCancelControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/cancel";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000721");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000721");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000729");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000721");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000721");
    private static final UUID WALK_IN_ID = UUID.fromString("60000000-0000-0000-0000-000000000721");
    private static final Instant CANCELLED_AT = Instant.parse("2030-06-20T03:40:00Z");

    private QueueCancelApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(QueueCancelApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("queue.cancel"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new QueueCancelController(
                applicationService,
                actorProvider,
                new QueueCancelApiMapper(),
                new QueueCancelApiErrorMapper()
            ))
            .build();
    }

    @Test
    void cancelsQueueTicketAndMapsRequestToCommand() throws Exception {
        when(applicationService.cancelQueueTicket(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", " idem-cancel ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cancelledAt": "2030-06-20T03:40:00Z",
                      "reasonCode": " GUEST_LEFT ",
                      "note": " guest went away "
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(21))
            .andExpect(jsonPath("$.queueTicketStatus").value("cancelled"))
            .andExpect(jsonPath("$.walkInId").value(WALK_IN_ID.toString()))
            .andExpect(jsonPath("$.cancelledAt").value(CANCELLED_AT.toString()))
            .andExpect(jsonPath("$.cancellationReasonCode").value("GUEST_LEFT"))
            .andExpect(jsonPath("$.alreadyCancelled").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.cancelled"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<CancelQueueTicketCommand> commandCaptor = ArgumentCaptor.forClass(CancelQueueTicketCommand.class);
        verify(applicationService).cancelQueueTicket(commandCaptor.capture());
        CancelQueueTicketCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.queueTicketId()).isEqualTo(QUEUE_TICKET_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-cancel");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.cancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(command.reasonCode()).isEqualTo("GUEST_LEFT");
        assertThat(command.note()).isEqualTo("guest went away");
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("queue.cancel.missing_idempotency_key"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void forbiddenWithoutQueueCancelPermission() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("queue.skip"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void endpointDeclaresAppGateQueueCancelPermission() throws Exception {
        Method method = QueueCancelController.class.getMethod(
            "cancelQueueTicket",
            UUID.class,
            UUID.class,
            String.class,
            CancelQueueTicketRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("queue.cancel");
    }

    @Test
    void requestDtoOnlyExposesCancelFields() {
        assertThat(CancelQueueTicketRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("cancelledAt", "reasonCode", "note");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(QueueCancelError.INVALID_COMMAND, 400, "INVALID_COMMAND");
        assertApplicationError(QueueCancelError.MISSING_IDEMPOTENCY_KEY, 400, "MISSING_IDEMPOTENCY_KEY");
        assertApplicationError(QueueCancelError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND");
        assertApplicationError(QueueCancelError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH");
        assertApplicationError(QueueCancelError.STORE_ACCESS_DENIED, 403, "FORBIDDEN");
        assertApplicationError(QueueCancelError.QUEUE_TICKET_NOT_FOUND, 404, "QUEUE_TICKET_NOT_FOUND");
        assertApplicationError(QueueCancelError.QUEUE_TICKET_CANNOT_CANCEL_SEATED, 409, "QUEUE_TICKET_CANNOT_CANCEL_SEATED");
        assertApplicationError(QueueCancelError.QUEUE_TICKET_CANNOT_CANCEL_EXPIRED, 409, "QUEUE_TICKET_CANNOT_CANCEL_EXPIRED");
        assertApplicationError(QueueCancelError.IDEMPOTENCY_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS");
        assertApplicationError(QueueCancelError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY");
        assertApplicationError(QueueCancelError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT");
        assertApplicationError(QueueCancelError.ILLEGAL_STATE_TRANSITION, 409, "ILLEGAL_STATE_TRANSITION");
        assertApplicationError(QueueCancelError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED");
        assertApplicationError(QueueCancelError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR");
    }

    private void assertApplicationError(QueueCancelError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        QueueCancelResult result = applicationError == QueueCancelError.IDEMPOTENCY_IN_PROGRESS
            ? QueueCancelResult.retryLater(applicationError)
            : QueueCancelResult.failure(applicationError);
        when(applicationService.cancelQueueTicket(any())).thenReturn(result);

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value("queue.cancel." + expectedApiCode.toLowerCase()));
    }

    private static QueueCancelResult success(boolean replayed) {
        if (replayed) {
            return QueueCancelResult.replay(
                QUEUE_TICKET_ID,
                21,
                null,
                null,
                null,
                WALK_IN_ID,
                CANCELLED_AT,
                "GUEST_LEFT",
                false
            );
        }
        return QueueCancelResult.success(
            QUEUE_TICKET_ID,
            21,
            null,
            null,
            null,
            WALK_IN_ID,
            CANCELLED_AT,
            "GUEST_LEFT",
            "completed",
            List.of("queue_ticket.cancelled"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        );
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
