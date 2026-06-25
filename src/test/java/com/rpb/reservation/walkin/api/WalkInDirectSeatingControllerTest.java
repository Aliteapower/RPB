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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.walkin.application.WalkInDirectSeatingError;
import com.rpb.reservation.walkin.application.WalkInDirectSeatingResult;
import com.rpb.reservation.walkin.application.command.SeatWalkInDirectlyCommand;
import com.rpb.reservation.walkin.application.service.WalkInDirectSeatingApplicationService;
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

class WalkInDirectSeatingControllerTest {

    private static final String ENDPOINT = "/api/v1/stores/{storeId}/walk-ins/direct-seating";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_GROUP_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID TEMP_TABLE_ID_A = UUID.fromString("70000000-0000-0000-0000-000000000981");
    private static final UUID TEMP_TABLE_ID_B = UUID.fromString("70000000-0000-0000-0000-000000000982");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WalkInDirectSeatingApplicationService applicationService;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(WalkInDirectSeatingApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("walkin.direct_seating.create"),
            Set.of(STORE_ID)
        ));
        WalkInDirectSeatingApiMapper apiMapper = new WalkInDirectSeatingApiMapper();
        WalkInDirectSeatingApiErrorMapper errorMapper = new WalkInDirectSeatingApiErrorMapper();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new WalkInDirectSeatingController(applicationService, actorProvider, apiMapper, errorMapper))
            .build();
    }

    @Test
    void seatsNoPhoneWalkInAndMapsRequestToCommand() throws Exception {
        UUID walkInId = UUID.randomUUID();
        UUID seatingId = UUID.randomUUID();
        when(applicationService.seatWalkInDirectly(any())).thenReturn(success(walkInId, seatingId, "dining_table", TABLE_ID, false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-1001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", "Boss friend", null, null, null, null, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.walkInId").value(walkInId.toString()))
            .andExpect(jsonPath("$.seatingId").value(seatingId.toString()))
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.partySize").value(2))
            .andExpect(jsonPath("$.status").value("occupied"))
            .andExpect(jsonPath("$.events[0]").value("walk_in.created"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<SeatWalkInDirectlyCommand> commandCaptor = ArgumentCaptor.forClass(SeatWalkInDirectlyCommand.class);
        verify(applicationService).seatWalkInDirectly(commandCaptor.capture());
        SeatWalkInDirectlyCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.partySize()).isEqualTo(2);
        assertThat(command.customerName()).isEqualTo("Guest");
        assertThat(command.customerNickname()).isEqualTo("Boss friend");
        assertThat(command.phoneE164()).isNull();
        assertThat(command.idempotencyKey()).isEqualTo("idem-1001");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
    }

    @Test
    void seatsWithSpecifiedTable() throws Exception {
        when(applicationService.seatWalkInDirectly(any())).thenReturn(success(UUID.randomUUID(), UUID.randomUUID(), "dining_table", TABLE_ID, false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, "+6591234567", TABLE_ID, null, null, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.type").value("TABLE"));

        ArgumentCaptor<SeatWalkInDirectlyCommand> commandCaptor = ArgumentCaptor.forClass(SeatWalkInDirectlyCommand.class);
        verify(applicationService).seatWalkInDirectly(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tableId()).isEqualTo(TABLE_ID);
        assertThat(commandCaptor.getValue().phoneE164()).isEqualTo("+6591234567");
    }

    @Test
    void seatsWithExistingTableGroup() throws Exception {
        when(applicationService.seatWalkInDirectly(any())).thenReturn(success(UUID.randomUUID(), UUID.randomUUID(), "table_group", TABLE_GROUP_ID, false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(4, null, "Group", null, null, null, TABLE_GROUP_ID, null, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.type").value("TABLE_GROUP"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_GROUP_ID.toString()));
    }

    @Test
    void seatsWithTemporaryTablesAndMapsMemberIds() throws Exception {
        when(applicationService.seatWalkInDirectly(any())).thenReturn(success(UUID.randomUUID(), UUID.randomUUID(), "table_group", TABLE_GROUP_ID, false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-temp-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 6,
                      "customerName": "Group",
                      "tableId": null,
                      "tableGroupId": null,
                      "temporaryTableIds": [
                        "%s",
                        "%s"
                      ]
                    }
                    """.formatted(TEMP_TABLE_ID_A, TEMP_TABLE_ID_B)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.type").value("TABLE_GROUP"));

        ArgumentCaptor<SeatWalkInDirectlyCommand> commandCaptor = ArgumentCaptor.forClass(SeatWalkInDirectlyCommand.class);
        verify(applicationService).seatWalkInDirectly(commandCaptor.capture());
        SeatWalkInDirectlyCommand command = commandCaptor.getValue();
        assertThat(command.tableId()).isNull();
        assertThat(command.tableGroupId()).isNull();
        assertThat(command.temporaryTableIds()).containsExactly(TEMP_TABLE_ID_A, TEMP_TABLE_ID_B);
    }

    @Test
    void autoSelectedTableSuccessUsesCreatedStatus() throws Exception {
        when(applicationService.seatWalkInDirectly(any())).thenReturn(success(UUID.randomUUID(), UUID.randomUUID(), "dining_table", TABLE_ID, false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-auto")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, null, null, null, null, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotency.replayed").value(false));
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.seatWalkInDirectly(any())).thenReturn(success(UUID.randomUUID(), UUID.randomUUID(), "dining_table", TABLE_ID, true));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, null, null, null, null, null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, null, null, null, null, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("walkin.direct_seating.missing_idempotency_key"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidPartySizeReturnsValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-party")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(0, null, "Guest", null, null, null, null, null, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARTY_SIZE"))
            .andExpect(jsonPath("$.error.messageKey").value("walkin.direct_seating.invalid_party_size"));
    }

    @Test
    void invalidPhoneReturnsValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, "91234567", null, null, null, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PHONE_E164"))
            .andExpect(jsonPath("$.error.messageKey").value("walkin.direct_seating.invalid_phone_e164"));
    }

    @Test
    void tableAndGroupTogetherReturnResourceConflict() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-resource-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, null, TABLE_ID, TABLE_GROUP_ID, null, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void temporaryTableSelectionValidationStopsBeforeService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-one-temp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"partySize": 6, "temporaryTableIds": ["%s"]}
                    """.formatted(TEMP_TABLE_ID_A)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED"));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-duplicate-temp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"partySize": 6, "temporaryTableIds": ["%s", "%s"]}
                    """.formatted(TEMP_TABLE_ID_A, TEMP_TABLE_ID_A)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE"));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-temp-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"partySize": 6, "tableId": "%s", "temporaryTableIds": ["%s", "%s"]}
                    """.formatted(TABLE_ID, TEMP_TABLE_ID_A, TEMP_TABLE_ID_B)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_CONFLICT"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void requestDtoExposesTemporaryTableIds() {
        assertThat(SeatWalkInDirectlyRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly(
                "partySize",
                "customerId",
                "customerName",
                "customerNickname",
                "phoneE164",
                "tableId",
                "tableGroupId",
                "temporaryTableIds",
                "overrideReasonCode",
                "overrideNote"
            );
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(WalkInDirectSeatingError.TABLE_LOCK_CONFLICT, 409, "TABLE_LOCK_CONFLICT");
        assertApplicationError(WalkInDirectSeatingError.TABLE_RESOURCE_UNAVAILABLE, 409, "TABLE_NOT_AVAILABLE");
        assertApplicationError(WalkInDirectSeatingError.PARTY_SIZE_OUTSIDE_CAPACITY, 409, "TABLE_CAPACITY_INSUFFICIENT");
        assertApplicationError(WalkInDirectSeatingError.INVALID_TABLE_GROUP, 409, "TABLE_GROUP_INVALID");
        assertApplicationError(WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED, 400, "TEMPORARY_TABLE_GROUP_MEMBER_REQUIRED");
        assertApplicationError(WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE, 400, "TEMPORARY_TABLE_GROUP_MEMBER_DUPLICATE");
        assertApplicationError(WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE, 409, "TEMPORARY_TABLE_GROUP_MEMBER_UNAVAILABLE");
        assertApplicationError(WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT, 409, "TEMPORARY_TABLE_GROUP_CAPACITY_INSUFFICIENT");
        assertApplicationError(WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_LOCK_CONFLICT, 409, "TEMPORARY_TABLE_GROUP_LOCK_CONFLICT");
        assertApplicationError(WalkInDirectSeatingError.TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT, 409, "TEMPORARY_TABLE_GROUP_PREASSIGNMENT_CONFLICT");
        assertApplicationError(WalkInDirectSeatingError.MANUAL_OVERRIDE_REQUIRED, 400, "OVERRIDE_REASON_REQUIRED");
        assertApplicationError(WalkInDirectSeatingError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS");
        assertApplicationError(WalkInDirectSeatingError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY");
        assertApplicationError(WalkInDirectSeatingError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    void mapsAdditionalRequiredApplicationErrors() throws Exception {
        assertApplicationError(WalkInDirectSeatingError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND");
        assertApplicationError(WalkInDirectSeatingError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH");
        assertApplicationError(WalkInDirectSeatingError.STORE_ACCESS_DENIED, 403, "FORBIDDEN");
        assertApplicationError(WalkInDirectSeatingError.INVALID_CUSTOMER_IDENTITY, 400, "INVALID_CUSTOMER_IDENTITY");
        assertApplicationError(WalkInDirectSeatingError.INVALID_SEATING_SOURCE, 409, "SEATING_SOURCE_INVALID");
        assertApplicationError(WalkInDirectSeatingError.INVALID_SEATING_RESOURCE, 409, "SEATING_RESOURCE_INVALID");
        assertApplicationError(WalkInDirectSeatingError.ILLEGAL_STATE_TRANSITION, 409, "ILLEGAL_STATE_TRANSITION");
        assertApplicationError(WalkInDirectSeatingError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED");
        assertApplicationError(WalkInDirectSeatingError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "customer",
            Set.of("customer"),
            Set.of("walkin.direct_seating.create"),
            Set.of(STORE_ID)
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, null, null, null, null, null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void storeScopeMismatchReturnsForbiddenWithoutCallingService() throws Exception {
        UUID otherStore = UUID.fromString("20000000-0000-0000-0000-000000000099");

        mockMvc.perform(post(ENDPOINT, otherStore)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, null, null, null, null, null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void noForbiddenVerticalSliceApiOrUiArtifactsAreCreated() throws Exception {
        List<String> sourceFiles = Files.walk(Path.of("src/main/java"))
            .filter(Files::isRegularFile)
            .map(Path::toString)
            .map(path -> path.replace('\\', '/'))
            .toList();

        assertThat(sourceFiles)
            .filteredOn(path -> path.endsWith("Controller.java"))
            .containsExactlyInAnyOrder(
                "src/main/java/com/rpb/reservation/walkin/api/WalkInDirectSeatingController.java",
                "src/main/java/com/rpb/reservation/walkin/api/WalkInQueueController.java",
                "src/main/java/com/rpb/reservation/cleaning/api/CleaningController.java",
                "src/main/java/com/rpb/reservation/customer/api/CustomerPhoneLookupController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueCallController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueCancelController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueRejoinController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueSkipController.java",
                "src/main/java/com/rpb/reservation/queue/api/QueueTicketListController.java",
                "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueController.java",
                "src/main/java/com/rpb/reservation/reservation/api/ReservationController.java",
                "src/main/java/com/rpb/reservation/reservation/api/ReservationTodayViewController.java",
                "src/main/java/com/rpb/reservation/table/api/TableResourceListController.java",
                "src/main/java/com/rpb/reservation/table/api/TemporaryTableGroupController.java",
                "src/main/java/com/rpb/reservation/table/api/TableSwitchController.java",
                "src/main/java/com/rpb/reservation/appgate/api/MeAppsController.java"
            );
        assertThat(sourceFiles)
            .noneMatch(WalkInDirectSeatingControllerTest::isForbiddenQueueApiFile);
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
                "src/components/reservation-workbench/CreateReservationDialog.vue",
                "src/components/reservation-workbench/ReservationMonthCalendar.vue",
                "src/components/reservation-workbench/ReservationQuickActionPanel.vue",
                "src/components/reservation-workbench/ReservationSeatDialog.vue",
                "src/components/reservation-workbench/ReservationTableSwitchDialog.vue",
                "src/components/reservation-workbench/ReservationTodayListItem.vue",
                "src/components/reservation-workbench/ReservationTodayListPanel.vue",
                "src/components/staff/StaffBottomNav.vue",
                "src/components/staff/StaffBusinessDateSwitcher.vue",
                "src/components/staff/StaffGuestContactLookup.vue",
                "src/components/staff/StaffGuestNameField.vue",
                "src/components/staff/StaffSingaporePhoneField.vue",
                "src/components/staff/StaffTimeWheelPicker.vue",
                "src/components/staff-home/StaffHomeActionGroup.vue",
                "src/components/staff-home/StaffHomeTopBar.vue",
                "src/components/staff-home/StaffHomeWorkflowStrip.vue",
                "src/components/staff-table/TableResourcePicker.vue",
                "src/pages/CleaningCompletePage.vue",
                "src/pages/QueueCallPage.vue",
                "src/pages/QueueTicketListPage.vue",
                "src/pages/ReservationArrivedDirectSeatingPage.vue",
                "src/pages/ReservationArrivedToQueuePage.vue",
                "src/pages/ReservationCheckInPage.vue",
                "src/pages/ReservationTodayViewPage.vue",
                "src/pages/SeatingFromCalledQueuePage.vue",
                "src/pages/StoreStaffHomePage.vue",
                "src/pages/TableResourceListPage.vue",
                "src/pages/WalkInDirectSeatingPage.vue",
                "src/pages/WalkInQueuePage.vue"
            );
        assertThat(vueFiles)
            .filteredOn(path -> !Set.of(
                "src/pages/QueueCallPage.vue",
                "src/pages/QueueTicketListPage.vue",
                "src/pages/CleaningCompletePage.vue",
                "src/pages/ReservationArrivedDirectSeatingPage.vue",
                "src/pages/ReservationArrivedToQueuePage.vue",
                "src/pages/ReservationCheckInPage.vue",
                "src/pages/ReservationTodayViewPage.vue",
                "src/pages/WalkInQueuePage.vue",
                "src/components/reservation-workbench/CreateReservationDialog.vue",
                "src/components/reservation-workbench/ReservationMonthCalendar.vue",
                "src/components/reservation-workbench/ReservationQuickActionPanel.vue",
                "src/components/reservation-workbench/ReservationSeatDialog.vue",
                "src/components/reservation-workbench/ReservationTableSwitchDialog.vue",
                "src/components/reservation-workbench/ReservationTodayListItem.vue",
                "src/components/reservation-workbench/ReservationTodayListPanel.vue",
                "src/pages/SeatingFromCalledQueuePage.vue"
            ).contains(path))
            .noneMatch(WalkInDirectSeatingControllerTest::isForbiddenUiFile);
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
            "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/SkipQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/QueueRejoinController.java",
            "src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketRequest.java",
            "src/main/java/com/rpb/reservation/queue/api/RejoinQueueTicketResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorCode.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiErrorResponse.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueApiMapper.java",
            "src/main/java/com/rpb/reservation/queue/api/SeatingFromCalledQueueController.java"
        ).contains(normalized);
    }

    private void assertApplicationError(WalkInDirectSeatingError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        when(applicationService.seatWalkInDirectly(any())).thenReturn(WalkInDirectSeatingResult.failure(applicationError));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SeatWalkInDirectlyRequest(2, null, "Guest", null, null, null, null, null, null, null))))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value("walkin.direct_seating." + expectedApiCode.toLowerCase()))
            .andExpect(jsonPath("$.error.details").isMap());
    }

    private String json(SeatWalkInDirectlyRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private static WalkInDirectSeatingResult success(UUID walkInId, UUID seatingId, String resourceType, UUID resourceId, boolean replayed) {
        if (replayed) {
            return WalkInDirectSeatingResult.replay(walkInId, seatingId, resourceType, resourceId, 2);
        }
        return WalkInDirectSeatingResult.success(
            walkInId,
            seatingId,
            resourceType,
            resourceId,
            2,
            "seated",
            "occupied",
            "active",
            "completed",
            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
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
