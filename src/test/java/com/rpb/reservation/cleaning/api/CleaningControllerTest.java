package com.rpb.reservation.cleaning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.cleaning.application.CleaningApplicationError;
import com.rpb.reservation.cleaning.application.CleaningApplicationResult;
import com.rpb.reservation.cleaning.application.command.CompleteCleaningCommand;
import com.rpb.reservation.cleaning.application.command.StartCleaningCommand;
import com.rpb.reservation.cleaning.application.service.CleaningApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.nio.file.Files;
import java.nio.file.Path;
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

class CleaningControllerTest {

    private static final String START_ENDPOINT = "/api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start";
    private static final String COMPLETE_ENDPOINT = "/api/v1/stores/{storeId}/cleanings/{cleaningId}/complete";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SEATING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID CLEANING_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_GROUP_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CleaningApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(CleaningApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("cleaning.start", "cleaning.complete"),
            Set.of(STORE_ID)
        ));
        CleaningApiMapper apiMapper = new CleaningApiMapper();
        CleaningApiErrorMapper errorMapper = new CleaningApiErrorMapper();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new CleaningController(applicationService, actorProvider, apiMapper, errorMapper))
            .build();
    }

    @Test
    void startsCleaningFromSeatingIdWithTableResourceAndMapsCommand() throws Exception {
        when(applicationService.startCleaning(any())).thenReturn(startSuccess("dining_table", TABLE_ID, false));

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new StartCleaningRequest("guest_left", "window table"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.cleaningId").value(CLEANING_ID.toString()))
            .andExpect(jsonPath("$.seatingId").value(SEATING_ID.toString()))
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.resource.label").doesNotExist())
            .andExpect(jsonPath("$.cleaningStatus").value("cleaning"))
            .andExpect(jsonPath("$.tableStatus").value("cleaning"))
            .andExpect(jsonPath("$.events[0]").value("cleaning.started"))
            .andExpect(jsonPath("$.events[1]").value("table.cleaning"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<StartCleaningCommand> commandCaptor = ArgumentCaptor.forClass(StartCleaningCommand.class);
        verify(applicationService).startCleaning(commandCaptor.capture());
        StartCleaningCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.seatingId()).isEqualTo(SEATING_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-start");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.reasonCode()).isEqualTo("guest_left");
        assertThat(command.note()).isEqualTo("window table");
    }

    @Test
    void startsCleaningFromSeatingIdWithTableGroupResource() throws Exception {
        when(applicationService.startCleaning(any())).thenReturn(startSuccess("table_group", TABLE_GROUP_ID, false));

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-start-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new StartCleaningRequest(null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.type").value("TABLE_GROUP"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_GROUP_ID.toString()));
    }

    @Test
    void startCompletedReplayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.startCleaning(any())).thenReturn(CleaningApplicationResult.replay(
            CLEANING_ID,
            SEATING_ID,
            "dining_table",
            TABLE_ID,
            "cleaning",
            "cleaning"
        ));

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-start-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new StartCleaningRequest(null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));
    }

    @Test
    void completesCleaningByCleaningIdWithTableResourceAndMapsCommand() throws Exception {
        when(applicationService.completeCleaning(any())).thenReturn(completeSuccess("dining_table", TABLE_ID, false));

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, CLEANING_ID)
                .header("Idempotency-Key", "idem-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CompleteCleaningRequest("done", "dry"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.cleaningId").value(CLEANING_ID.toString()))
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.cleaningStatus").value("released"))
            .andExpect(jsonPath("$.tableStatus").value("available"))
            .andExpect(jsonPath("$.events[0]").value("cleaning.completed"))
            .andExpect(jsonPath("$.events[1]").value("table.available"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<CompleteCleaningCommand> commandCaptor = ArgumentCaptor.forClass(CompleteCleaningCommand.class);
        verify(applicationService).completeCleaning(commandCaptor.capture());
        CompleteCleaningCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.cleaningId()).isEqualTo(CLEANING_ID);
        assertThat(command.idempotencyKey()).isEqualTo("idem-complete");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.reasonCode()).isEqualTo("done");
        assertThat(command.note()).isEqualTo("dry");
    }

    @Test
    void completesCleaningByCleaningIdWithTableGroupResource() throws Exception {
        when(applicationService.completeCleaning(any())).thenReturn(completeSuccess("table_group", TABLE_GROUP_ID, false));

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, CLEANING_ID)
                .header("Idempotency-Key", "idem-complete-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CompleteCleaningRequest(null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resource.type").value("TABLE_GROUP"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_GROUP_ID.toString()));
    }

    @Test
    void completeCompletedReplayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.completeCleaning(any())).thenReturn(CleaningApplicationResult.replay(
            CLEANING_ID,
            SEATING_ID,
            "dining_table",
            TABLE_ID,
            "available",
            "released"
        ));

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, CLEANING_ID)
                .header("Idempotency-Key", "idem-complete-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CompleteCleaningRequest(null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));
    }

    @Test
    void missingIdempotencyKeyOnStartReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(START_ENDPOINT, STORE_ID, SEATING_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new StartCleaningRequest(null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("cleaning.missing_idempotency_key"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingIdempotencyKeyOnCompleteReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, CLEANING_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CompleteCleaningRequest(null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("cleaning.missing_idempotency_key"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void requestDtosOnlyExposeReasonAndNote() {
        assertThat(StartCleaningRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("reasonCode", "note");
        assertThat(CompleteCleaningRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("reasonCode", "note");
    }

    @Test
    void mapsStartApplicationErrorsToApiErrors() throws Exception {
        assertStartApplicationError(CleaningApplicationError.SEATING_NOT_FOUND, 404, "SEATING_NOT_FOUND");
        assertStartApplicationError(CleaningApplicationError.SEATING_RESOURCE_NOT_FOUND, 409, "SEATING_RESOURCE_NOT_FOUND");
        assertStartApplicationError(CleaningApplicationError.TABLE_NOT_OCCUPIED, 409, "TABLE_NOT_OCCUPIED");
        assertStartApplicationError(CleaningApplicationError.INVALID_TABLE_GROUP, 409, "TABLE_GROUP_INVALID");
        assertStartApplicationError(CleaningApplicationError.CLEANING_ALREADY_ACTIVE, 409, "CLEANING_ALREADY_ACTIVE");
        assertStartApplicationError(CleaningApplicationError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS");
        assertStartApplicationError(CleaningApplicationError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY");
        assertStartApplicationError(CleaningApplicationError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    void mapsCompleteApplicationErrorsToApiErrors() throws Exception {
        assertCompleteApplicationError(CleaningApplicationError.CLEANING_NOT_FOUND, 404, "CLEANING_NOT_FOUND");
        assertCompleteApplicationError(CleaningApplicationError.CLEANING_ALREADY_COMPLETED, 409, "CLEANING_ALREADY_COMPLETED");
        assertCompleteApplicationError(CleaningApplicationError.TABLE_NOT_CLEANING, 409, "TABLE_NOT_CLEANING");
        assertCompleteApplicationError(CleaningApplicationError.INVALID_TABLE_GROUP, 409, "TABLE_GROUP_INVALID");
        assertCompleteApplicationError(CleaningApplicationError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS");
        assertCompleteApplicationError(CleaningApplicationError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY");
        assertCompleteApplicationError(CleaningApplicationError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    void mapsAdditionalRequiredApplicationErrors() throws Exception {
        assertStartApplicationError(CleaningApplicationError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND");
        assertStartApplicationError(CleaningApplicationError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH");
        assertStartApplicationError(CleaningApplicationError.STORE_ACCESS_DENIED, 403, "FORBIDDEN");
        assertStartApplicationError(CleaningApplicationError.RESOURCE_TARGET_INVALID, 409, "CLEANING_TARGET_INVALID");
        assertStartApplicationError(CleaningApplicationError.TABLE_NOT_FOUND, 404, "TABLE_NOT_FOUND");
        assertStartApplicationError(CleaningApplicationError.ILLEGAL_STATE_TRANSITION, 409, "ILLEGAL_STATE_TRANSITION");
        assertStartApplicationError(CleaningApplicationError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED");
        assertStartApplicationError(CleaningApplicationError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR");
        assertStartApplicationError(CleaningApplicationError.BUSINESS_EVENT_WRITE_FAILED, 500, "PERSISTENCE_ERROR");
        assertStartApplicationError(CleaningApplicationError.STATE_TRANSITION_WRITE_FAILED, 500, "PERSISTENCE_ERROR");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "customer",
            Set.of("customer"),
            Set.of("cleaning.start", "cleaning.complete"),
            Set.of(STORE_ID)
        );

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new StartCleaningRequest(null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingPermissionReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("cleaning.start"),
            Set.of(STORE_ID)
        );

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, CLEANING_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CompleteCleaningRequest(null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        UUID otherStore = UUID.fromString("20000000-0000-0000-0000-000000000099");

        mockMvc.perform(post(START_ENDPOINT, otherStore, SEATING_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new StartCleaningRequest(null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void noOtherVerticalSliceApiOrUiArtifactsAreCreated() throws Exception {
        List<String> sourceFiles = Files.walk(Path.of("src/main/java"))
            .filter(Files::isRegularFile)
            .map(Path::toString)
            .map(path -> path.replace('\\', '/'))
            .toList();

        assertThat(sourceFiles)
            .filteredOn(path -> path.endsWith("Controller.java"))
            .containsExactlyInAnyOrder(
                "src/main/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingController.java",
                "src/main/java/com/rpb/reservation/cleaning/api/CleaningController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueCallController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueSkipController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueTicketListController.java",
                "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueController.java",
                "src/main/java/com/rpb/reservation/reservation/api/ReservationController.java",
                "src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java",
                "src/main/java/com/rpb/reservation/appgate/api/MeAppsController.java"
            );
        assertThat(sourceFiles)
            .noneMatch(CleaningControllerTest::isForbiddenQueueApiFile);
        assertThat(sourceFiles)
            .noneMatch(path -> path.contains("/turnover/api/"));

        List<String> vueFiles = Files.walk(Path.of("src"))
            .filter(Files::isRegularFile)
            .map(Path::toString)
            .map(path -> path.replace('\\', '/'))
            .filter(path -> path.endsWith(".vue"))
            .toList();

        assertThat(vueFiles)
            .containsExactlyInAnyOrder(
                "src/App.vue",
                "src/components/DateTimeWheelPicker.vue",
                "src/pages/CleaningCompletePage.vue",
                "src/pages/QueueCallPage.vue",
                "src/pages/QueueTicketListPage.vue",
                "src/pages/ReservationArrivedDirectSeatingPage.vue",
                "src/pages/ReservationArrivedToQueuePage.vue",
                "src/pages/ReservationCheckInPage.vue",
                "src/pages/ReservationCreatePage.vue",
                "src/pages/ReservationTodayViewPage.vue",
                "src/pages/SeatingFromCalledQueuePage.vue",
                "src/pages/StoreStaffHomePage.vue",
                "src/pages/WalkInDirectSeatingPage.vue"
            );
        assertThat(vueFiles)
            .filteredOn(path -> !Set.of(
                "src/pages/CleaningCompletePage.vue",
                "src/pages/QueueCallPage.vue",
                "src/pages/QueueTicketListPage.vue",
                "src/pages/ReservationArrivedDirectSeatingPage.vue",
                "src/pages/ReservationArrivedToQueuePage.vue",
                "src/pages/ReservationCheckInPage.vue",
                "src/pages/ReservationCreatePage.vue",
                "src/pages/ReservationTodayViewPage.vue",
                "src/pages/SeatingFromCalledQueuePage.vue"
            ).contains(path))
            .noneMatch(CleaningControllerTest::isForbiddenUiFile);
    }

    private static boolean isForbiddenQueueApiFile(String path) {
        String normalized = path.replace('\\', '/');
        if (!normalized.contains("/queue/api/")) {
            return false;
        }
        return !Set.of(
            "src/main/java/com/rpb/reservation/queue/api/CallQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/CallQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatCalledQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueCallController.java",
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
            "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueController.java"
        ).contains(normalized);
    }

    private void assertStartApplicationError(CleaningApplicationError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        when(applicationService.startCleaning(any())).thenReturn(CleaningApplicationResult.failure(applicationError));

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "idem-start-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new StartCleaningRequest(null, null))))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value(messageKey(expectedApiCode)))
            .andExpect(jsonPath("$.error.details").isMap());
    }

    private void assertCompleteApplicationError(CleaningApplicationError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        when(applicationService.completeCleaning(any())).thenReturn(CleaningApplicationResult.failure(applicationError));

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, CLEANING_ID)
                .header("Idempotency-Key", "idem-complete-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CompleteCleaningRequest(null, null))))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value(messageKey(expectedApiCode)))
            .andExpect(jsonPath("$.error.details").isMap());
    }

    private String json(StartCleaningRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String json(CompleteCleaningRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private static CleaningApplicationResult startSuccess(String resourceType, UUID resourceId, boolean replayed) {
        if (replayed) {
            return CleaningApplicationResult.replay(CLEANING_ID, SEATING_ID, resourceType, resourceId, "cleaning", "cleaning");
        }
        return CleaningApplicationResult.success(
            CLEANING_ID,
            SEATING_ID,
            resourceType,
            resourceId,
            "occupied",
            "cleaning",
            "cleaning",
            "completed",
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static CleaningApplicationResult completeSuccess(String resourceType, UUID resourceId, boolean replayed) {
        if (replayed) {
            return CleaningApplicationResult.replay(CLEANING_ID, SEATING_ID, resourceType, resourceId, "available", "released");
        }
        return CleaningApplicationResult.success(
            CLEANING_ID,
            SEATING_ID,
            resourceType,
            resourceId,
            "cleaning",
            "available",
            "released",
            "completed",
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String messageKey(String apiCode) {
        return switch (apiCode) {
            case "CLEANING_NOT_FOUND" -> "cleaning.not_found";
            case "CLEANING_ALREADY_ACTIVE" -> "cleaning.already_active";
            case "CLEANING_ALREADY_COMPLETED" -> "cleaning.already_completed";
            case "CLEANING_TARGET_INVALID" -> "cleaning.target_invalid";
            default -> "cleaning." + apiCode.toLowerCase();
        };
    }

    private static boolean isForbiddenUiFile(String path) {
        String fileName = Path.of(path).getFileName().toString().toLowerCase();
        return fileName.contains("reservation")
            || fileName.contains("queue")
            || fileName.contains("cleaning")
            || fileName.contains("turnover")
            || fileName.contains("payment")
            || fileName.contains("pos")
            || fileName.contains("marketing")
            || fileName.contains("member")
            || fileName.contains("tablemap")
            || fileName.contains("drag");
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
