package com.rpb.reservation.walkin.api;

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
import com.rpb.reservation.walkin.application.WalkInQueueError;
import com.rpb.reservation.walkin.application.WalkInQueueResult;
import com.rpb.reservation.walkin.application.command.QueueWalkInCommand;
import com.rpb.reservation.walkin.application.service.WalkInQueueApplicationService;
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

class WalkInQueueControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/walk-ins/queue";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000711");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000711");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000719");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000711");
    private static final UUID WALK_IN_ID = UUID.fromString("60000000-0000-0000-0000-000000000711");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000711");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");

    private WalkInQueueApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(WalkInQueueApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("walkin.queue.create"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new WalkInQueueController(
                applicationService,
                actorProvider,
                new WalkInQueueApiMapper(),
                new WalkInQueueApiErrorMapper()
            ))
            .build();
    }

    @Test
    void createsWalkInQueueTicketAndMapsRequestToCommand() throws Exception {
        when(applicationService.queueWalkIn(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", " idem-walk-in-queue ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 3,
                      "customerName": "  Zhao  ",
                      "customerNickname": " VIP ",
                      "phoneE164": "+6591234567",
                      "note": " near window "
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.walkInId").value(WALK_IN_ID.toString()))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(18))
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.partySize").value(3))
            .andExpect(jsonPath("$.partySizeGroup").value("3-4"))
            .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
            .andExpect(jsonPath("$.queuePosition").value(5))
            .andExpect(jsonPath("$.events[0]").value("walk_in.queued"))
            .andExpect(jsonPath("$.events[1]").value("queue_ticket.created"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<QueueWalkInCommand> commandCaptor = ArgumentCaptor.forClass(QueueWalkInCommand.class);
        verify(applicationService).queueWalkIn(commandCaptor.capture());
        QueueWalkInCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.partySize()).isEqualTo(3);
        assertThat(command.customerName()).isEqualTo("Zhao");
        assertThat(command.customerNickname()).isEqualTo("VIP");
        assertThat(command.phoneE164()).isEqualTo("+6591234567");
        assertThat(command.note()).isEqualTo("near window");
        assertThat(command.idempotencyKey()).isEqualTo("idem-walk-in-queue");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partySize\":2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("walkin.queue.missing_idempotency_key"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidPartySizeReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-party")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partySize\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARTY_SIZE"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void forbiddenWithoutWalkInQueuePermission() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("walkin.direct_seating.create"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partySize\":2}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partySize\":2}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void endpointDeclaresAppGateWalkInQueueCreatePermission() throws Exception {
        Method method = WalkInQueueController.class.getMethod(
            "queueWalkIn",
            UUID.class,
            String.class,
            QueueWalkInRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("walkin.queue.create");
    }

    @Test
    void requestDtoOnlyExposesWalkInQueueFields() {
        assertThat(QueueWalkInRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("partySize", "customerId", "customerName", "customerNickname", "phoneE164", "note");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(WalkInQueueError.INVALID_COMMAND, 400, "INVALID_COMMAND");
        assertApplicationError(WalkInQueueError.MISSING_IDEMPOTENCY_KEY, 400, "MISSING_IDEMPOTENCY_KEY");
        assertApplicationError(WalkInQueueError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND");
        assertApplicationError(WalkInQueueError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH");
        assertApplicationError(WalkInQueueError.STORE_ACCESS_DENIED, 403, "FORBIDDEN");
        assertApplicationError(WalkInQueueError.INVALID_CUSTOMER_IDENTITY, 400, "INVALID_CUSTOMER_IDENTITY");
        assertApplicationError(WalkInQueueError.QUEUE_GROUP_NOT_FOUND, 404, "QUEUE_GROUP_NOT_FOUND");
        assertApplicationError(WalkInQueueError.IDEMPOTENCY_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS");
        assertApplicationError(WalkInQueueError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY");
        assertApplicationError(WalkInQueueError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT");
        assertApplicationError(WalkInQueueError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED");
        assertApplicationError(WalkInQueueError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR");
    }

    private void assertApplicationError(WalkInQueueError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        WalkInQueueResult result = applicationError == WalkInQueueError.IDEMPOTENCY_IN_PROGRESS
            ? WalkInQueueResult.retryLater(applicationError)
            : WalkInQueueResult.failure(applicationError);
        when(applicationService.queueWalkIn(any())).thenReturn(result);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partySize\":2}"))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value("walkin.queue." + expectedApiCode.toLowerCase()));
    }

    private static WalkInQueueResult success(boolean replayed) {
        if (replayed) {
            return WalkInQueueResult.replay(WALK_IN_ID, QUEUE_TICKET_ID, 18, 3, "3-4", BUSINESS_DATE, 5);
        }
        return WalkInQueueResult.success(
            WALK_IN_ID,
            QUEUE_TICKET_ID,
            18,
            3,
            "3-4",
            BUSINESS_DATE,
            5,
            "completed",
            List.of("walk_in.queued", "queue_ticket.created"),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
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
