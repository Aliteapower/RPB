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
import com.rpb.reservation.queue.application.QueueCallError;
import com.rpb.reservation.queue.application.QueueCallResult;
import com.rpb.reservation.queue.application.command.CallQueueTicketCommand;
import com.rpb.reservation.queue.application.service.QueueCallApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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

class QueueCallControllerTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/queue-tickets/{queueTicketId}/call";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000989");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000981");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000981");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000981");
    private static final Instant CALLED_AT = Instant.parse("2030-06-20T03:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:33:00Z");
    private static final Set<String> QUEUE_CALL_API_FILES = Set.of(
        "src/main/java/com/rpb/reservation/queue/api/CallQueueTicketRequest.java",
        "src/main/java/com/rpb/reservation/queue/api/CallQueueTicketResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketRequest.java",
        "src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorCode.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCallApiMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCallController.java",
        "src/main/java/com/rpb/reservation/queue/api/CancelQueueTicketRequest.java",
        "src/main/java/com/rpb/reservation/queue/api/CancelQueueTicketResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiErrorCode.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiErrorMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiErrorResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCancelApiMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueCancelController.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiErrorCode.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiErrorMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiErrorResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueTicketListApiMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueTicketListController.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueTicketListResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorCode.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiErrorResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueSkipApiMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueSkipController.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorCode.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/QueueRejoinController.java",
        "src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketRequest.java",
        "src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketRequest.java",
        "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorCode.java",
        "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorResponse.java",
        "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiMapper.java",
        "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueController.java"
    );

    private QueueCallApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(QueueCallApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(actor(Set.of("store_staff"), Set.of("queue.call"), Set.of(STORE_ID)));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new QueueCallController(
                applicationService,
                actorProvider,
                new QueueCallApiMapper(),
                new QueueCallApiErrorMapper()
            ))
            .build();
    }

    @Test
    void callsWaitingQueueTicketAndMapsRequestToCommand() throws Exception {
        when(applicationService.callQueueTicket(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", " idem-call ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "calledAt": "2030-06-20T03:30:00Z",
                      "reasonCode": " TABLE_READY ",
                      "note": " Call customer near entrance "
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(12))
            .andExpect(jsonPath("$.queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-CALL-0981"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.calledAt").value(CALLED_AT.toString()))
            .andExpect(jsonPath("$.holdUntilAt").value(HOLD_UNTIL_AT.toString()))
            .andExpect(jsonPath("$.alreadyCalled").value(false))
            .andExpect(jsonPath("$.events[0]").value("queue_ticket.called"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<CallQueueTicketCommand> commandCaptor = ArgumentCaptor.forClass(CallQueueTicketCommand.class);
        verify(applicationService).callQueueTicket(commandCaptor.capture());
        CallQueueTicketCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.queueTicketId()).isEqualTo(QUEUE_TICKET_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-call");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.calledAt()).isEqualTo(CALLED_AT);
        assertThat(command.reasonCode()).isEqualTo("TABLE_READY");
        assertThat(command.note()).isEqualTo("Call customer near entrance");
    }

    @Test
    void nullBodyUsesOnlyPathHeaderAndActorScope() throws Exception {
        when(applicationService.callQueueTicket(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-null-body")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        ArgumentCaptor<CallQueueTicketCommand> commandCaptor = ArgumentCaptor.forClass(CallQueueTicketCommand.class);
        verify(applicationService).callQueueTicket(commandCaptor.capture());
        assertThat(commandCaptor.getValue().calledAt()).isNull();
        assertThat(commandCaptor.getValue().reasonCode()).isNull();
        assertThat(commandCaptor.getValue().note()).isNull();
    }

    @Test
    void alreadyCalledReturnsOkWithoutEvents() throws Exception {
        when(applicationService.callQueueTicket(any())).thenReturn(QueueCallResult.alreadyCalled(
            QUEUE_TICKET_ID,
            12,
            RESERVATION_ID,
            "R-CALL-0981",
            CALLED_AT,
            HOLD_UNTIL_AT,
            "completed"
        ));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-already-called")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketStatus").value("called"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.alreadyCalled").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.callQueueTicket(any())).thenReturn(success(true));

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
            .andExpect(jsonPath("$.error.messageKey").value("queue.call.missing_idempotency_key"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void requestDtoOnlyExposesAllowedFields() {
        assertThat(CallQueueTicketRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("calledAt", "reasonCode", "note");
    }

    @Test
    void boundaryArtifactsRemainLimitedToApprovedQueueApis() throws Exception {
        try (var paths = Files.walk(Path.of("src", "main", "java", "com", "rpb", "reservation", "queue", "api"))) {
            assertThat(paths.filter(Files::isRegularFile).map(path -> path.toString().replace('\\', '/')).toList())
                .containsExactlyInAnyOrderElementsOf(QUEUE_CALL_API_FILES);
        }

        try (var paths = Files.walk(Path.of("src"))) {
            assertThat(paths.filter(Files::isRegularFile).map(path -> path.toString().replace('\\', '/')).toList())
                .noneMatch(path -> path.endsWith(".vue")
                    && Set.of(
                        "src/pages/QueueSkipPage.vue",
                        "src/pages/QueueRejoinPage.vue",
                        "src/pages/QueueDisplayPage.vue",
                        "src/pages/QueueWorkbenchPage.vue"
                    ).contains(path));
        }

        try (var paths = Files.walk(Path.of("src", "main", "resources", "db", "migration"))) {
            assertThat(paths.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList())
                .noneMatch(path -> path.startsWith("V003") && !"V003__auth_minimal_login.sql".equals(path));
        }
    }

    @Test
    void endpointDeclaresAppGateReservationQueueCallPermission() throws Exception {
        Method method = QueueCallController.class.getMethod(
            "callQueueTicket",
            UUID.class,
            UUID.class,
            String.class,
            CallQueueTicketRequest.class
        );

        RequireAppGate annotation = method.getAnnotation(RequireAppGate.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.appKey()).isEqualTo("reservation_queue");
        assertThat(annotation.permission()).isEqualTo("queue.call");
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(QueueCallError.INVALID_COMMAND, 400, "INVALID_COMMAND", "failed");
        assertApplicationError(QueueCallError.MISSING_IDEMPOTENCY_KEY, 400, "MISSING_IDEMPOTENCY_KEY", "failed");
        assertApplicationError(QueueCallError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND", "failed");
        assertApplicationError(QueueCallError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH", "failed");
        assertApplicationError(QueueCallError.STORE_ACCESS_DENIED, 403, "FORBIDDEN", "failed");
        assertApplicationError(QueueCallError.QUEUE_TICKET_NOT_FOUND, 404, "QUEUE_TICKET_NOT_FOUND", "failed");
        assertApplicationError(QueueCallError.QUEUE_TICKET_STATUS_NOT_WAITING, 409, "QUEUE_TICKET_STATUS_NOT_WAITING", "failed");
        assertApplicationError(QueueCallError.QUEUE_CALL_EVIDENCE_INCOMPLETE, 409, "QUEUE_CALL_EVIDENCE_INCOMPLETE", "failed");
        assertApplicationError(QueueCallError.QUEUE_TICKET_CANNOT_CALL_SEATED, 409, "QUEUE_TICKET_CANNOT_CALL_SEATED", "failed");
        assertApplicationError(QueueCallError.QUEUE_TICKET_CANNOT_CALL_CANCELLED, 409, "QUEUE_TICKET_CANNOT_CALL_CANCELLED", "failed");
        assertApplicationError(QueueCallError.QUEUE_TICKET_CANNOT_CALL_EXPIRED, 409, "QUEUE_TICKET_CANNOT_CALL_EXPIRED", "failed");
        assertApplicationError(QueueCallError.RESERVATION_NOT_FOUND, 404, "RESERVATION_NOT_FOUND", "failed");
        assertApplicationError(QueueCallError.RESERVATION_STATUS_NOT_ARRIVED, 409, "RESERVATION_STATUS_NOT_ARRIVED", "failed");
        assertApplicationError(QueueCallError.QUEUE_CALL_HOLD_POLICY_INVALID, 409, "QUEUE_CALL_HOLD_POLICY_INVALID", "failed");
        assertApplicationError(QueueCallError.IDEMPOTENCY_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS", "started");
        assertApplicationError(QueueCallError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed");
        assertApplicationError(QueueCallError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT", "conflict");
        assertApplicationError(QueueCallError.ILLEGAL_STATE_TRANSITION, 409, "ILLEGAL_STATE_TRANSITION", "failed");
        assertApplicationError(QueueCallError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED", "failed");
        assertApplicationError(QueueCallError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED", "failed");
        assertApplicationError(QueueCallError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED", "failed");
        assertApplicationError(QueueCallError.PERSISTENCE_ERROR, 500, "PERSISTENCE_ERROR", "failed");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("customer"), Set.of("queue.call"), Set.of(STORE_ID));

        mockMvc.perform(post(ENDPOINT, STORE_ID, QUEUE_TICKET_ID)
                .header("Idempotency-Key", "idem-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingQueueCallPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("reservation.queue"), Set.of(STORE_ID));

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
        QueueCallError applicationError,
        int expectedStatus,
        String expectedApiCode,
        String expectedIdempotencyStatus
    ) throws Exception {
        QueueCallResult result = applicationError == QueueCallError.IDEMPOTENCY_IN_PROGRESS
            ? QueueCallResult.retryLater(applicationError)
            : QueueCallResult.failure(applicationError);
        when(applicationService.callQueueTicket(any())).thenReturn(result);

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

    private static QueueCallResult success(boolean replayed) {
        if (replayed) {
            return QueueCallResult.replay(
                QUEUE_TICKET_ID,
                12,
                "called",
                RESERVATION_ID,
                "R-CALL-0981",
                "arrived",
                CALLED_AT,
                HOLD_UNTIL_AT,
                false
            );
        }
        return QueueCallResult.success(
            QUEUE_TICKET_ID,
            12,
            RESERVATION_ID,
            "R-CALL-0981",
            CALLED_AT,
            HOLD_UNTIL_AT,
            "completed",
            List.of("queue_ticket.called"),
            List.of(UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String validRequestJson() {
        return """
            {
              "calledAt": "2030-06-20T03:30:00Z",
              "reasonCode": "TABLE_READY",
              "note": "Call customer near entrance"
            }
            """;
    }

    private static String messageKey(String apiCode) {
        return "queue.call." + apiCode.toLowerCase();
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
