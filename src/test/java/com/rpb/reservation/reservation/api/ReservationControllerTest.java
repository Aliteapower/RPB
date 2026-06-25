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

import com.rpb.reservation.reservation.application.ReservationCreateError;
import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCancelApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCompleteApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationNoShowApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

class ReservationControllerTest {

    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID TABLE_GROUP_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final Instant RESERVED_START_AT = Instant.parse("2030-06-20T11:00:00Z");
    private static final Instant RESERVED_END_AT = Instant.parse("2030-06-20T12:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T11:15:00Z");

    private ReservationCreateApplicationService applicationService;
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
        applicationService = mock(ReservationCreateApplicationService.class);
        checkInApplicationService = mock(ReservationCheckInApplicationService.class);
        seatingApplicationService = mock(ReservationArrivedDirectSeatingApplicationService.class);
        queueApplicationService = mock(ReservationArrivedToQueueApplicationService.class);
        cancelApplicationService = mock(ReservationCancelApplicationService.class);
        noShowApplicationService = mock(ReservationNoShowApplicationService.class);
        completeApplicationService = mock(ReservationCompleteApplicationService.class);
        actorProvider = new MutableCurrentActorProvider(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("reservation.create"),
            Set.of(STORE_ID)
        ));
        ReservationApiMapper apiMapper = new ReservationApiMapper();
        ReservationApiErrorMapper errorMapper = new ReservationApiErrorMapper();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReservationController(
                applicationService,
                checkInApplicationService,
                seatingApplicationService,
                queueApplicationService,
                cancelApplicationService,
                noShowApplicationService,
                completeApplicationService,
                actorProvider,
                apiMapper,
                errorMapper,
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
    void createsConfirmedReservationAndMapsRequestToCommand() throws Exception {
        when(applicationService.createReservation(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", " idem-create ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 4,
                      "reservedStartAt": "2030-06-20T11:00:00Z",
                      "reservedEndAt": null,
                      "customerId": null,
                      "customerName": " Guest ",
                      "customerNickname": " VIP friend ",
                      "phoneE164": "+6591234567",
                      "note": " Window seat ",
                      "tableId": "%s",
                      "tableGroupId": null
                    }
                    """.formatted(TABLE_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-20300620-0007"))
            .andExpect(jsonPath("$.status").value("confirmed"))
            .andExpect(jsonPath("$.partySize").value(4))
            .andExpect(jsonPath("$.reservedStartAt").value("2030-06-20T11:00:00Z"))
            .andExpect(jsonPath("$.reservedEndAt").value("2030-06-20T12:30:00Z"))
            .andExpect(jsonPath("$.holdUntilAt").value("2030-06-20T11:15:00Z"))
            .andExpect(jsonPath("$.businessDate").value("2030-06-20"))
            .andExpect(jsonPath("$.customer.id").value(CUSTOMER_ID.toString()))
            .andExpect(jsonPath("$.customer.displayName").value("Guest"))
            .andExpect(jsonPath("$.customer.phoneE164").value("+6591234567"))
            .andExpect(jsonPath("$.events[0]").value("reservation.created"))
            .andExpect(jsonPath("$.events[1]").value("reservation.confirmed"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        ArgumentCaptor<CreateReservationCommand> commandCaptor = ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(applicationService).createReservation(commandCaptor.capture());
        CreateReservationCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT_ID);
        assertThat(command.storeId()).isEqualTo(STORE_ID);
        assertThat(command.partySize()).isEqualTo(4);
        assertThat(command.reservedStartAt()).isEqualTo(RESERVED_START_AT);
        assertThat(command.reservedEndAt()).isNull();
        assertThat(command.customerId()).isNull();
        assertThat(command.customerName()).isEqualTo("Guest");
        assertThat(command.customerNickname()).isEqualTo("VIP friend");
        assertThat(command.phoneE164()).isEqualTo("+6591234567");
        assertThat(command.note()).isEqualTo("Window seat");
        assertThat(command.idempotencyKey()).isEqualTo("idem-create");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorType()).isEqualTo("staff");
        assertThat(command.reservationCode()).isNull();
        assertThat(command.source()).isEqualTo("staff");
        assertThat(command.reasonCode()).isNull();
        assertThat(command.tableId()).isEqualTo(TABLE_ID);
        assertThat(command.tableGroupId()).isNull();
    }

    @Test
    void mapsTableGroupPreassignmentRequestToCommand() throws Exception {
        when(applicationService.createReservation(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-create-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 8,
                      "reservedStartAt": "2030-06-20T11:00:00Z",
                      "reservedEndAt": "2030-06-20T12:30:00Z",
                      "customerId": "%s",
                      "tableId": null,
                      "tableGroupId": "%s"
                    }
                    """.formatted(CUSTOMER_ID, TABLE_GROUP_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<CreateReservationCommand> commandCaptor = ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(applicationService).createReservation(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tableId()).isNull();
        assertThat(commandCaptor.getValue().tableGroupId()).isEqualTo(TABLE_GROUP_ID);
    }

    @Test
    void rejectsRequestWithBothTableAndTableGroupBeforeCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-resource-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 4,
                      "reservedStartAt": "2030-06-20T11:00:00Z",
                      "reservedEndAt": "2030-06-20T12:30:00Z",
                      "tableId": "%s",
                      "tableGroupId": "%s"
                    }
                    """.formatted(TABLE_ID, TABLE_GROUP_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_SELECTION_CONFLICT"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void createsReservationForExistingCustomerWithoutPhone() throws Exception {
        when(applicationService.createReservation(any())).thenReturn(success(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-existing-customer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 2,
                      "reservedStartAt": "2030-06-20T11:00:00Z",
                      "reservedEndAt": "2030-06-20T12:30:00Z",
                      "customerId": "%s",
                      "customerName": null,
                      "customerNickname": null,
                      "phoneE164": null,
                      "note": null
                    }
                    """.formatted(CUSTOMER_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customer.id").value(CUSTOMER_ID.toString()))
            .andExpect(jsonPath("$.customer.phoneE164").doesNotExist());

        ArgumentCaptor<CreateReservationCommand> commandCaptor = ArgumentCaptor.forClass(CreateReservationCommand.class);
        verify(applicationService).createReservation(commandCaptor.capture());
        assertThat(commandCaptor.getValue().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(commandCaptor.getValue().phoneE164()).isNull();
    }

    @Test
    void completedIdempotencyReplayReturnsOkAndReplayedTrue() throws Exception {
        when(applicationService.createReservation(any())).thenReturn(success(true));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
            .andExpect(jsonPath("$.events[0]").value("reservation.created"))
            .andExpect(jsonPath("$.events[1]").value("reservation.confirmed"));
    }

    @Test
    void missingIdempotencyKeyReturnsValidationErrorWithoutCallingService() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.missing_idempotency_key"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidPartySizeReturnsValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-party")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 0,
                      "reservedStartAt": "2030-06-20T11:00:00Z",
                      "reservedEndAt": "2030-06-20T12:30:00Z"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARTY_SIZE"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.invalid_party_size"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidTimeRangeReturnsValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-time")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 4,
                      "reservedStartAt": "2030-06-20T12:30:00Z",
                      "reservedEndAt": "2030-06-20T11:00:00Z"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_TIME_RANGE"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void invalidPhoneReturnsValidationError() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 4,
                      "reservedStartAt": "2030-06-20T11:00:00Z",
                      "reservedEndAt": "2030-06-20T12:30:00Z",
                      "phoneE164": "91234567"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PHONE_E164"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.invalid_phone_e164"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void requestDtoExcludesArrivalQueueSeatingAndTableAssignmentFields() {
        assertThat(CreateReservationRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly(
                "partySize",
                "reservedStartAt",
                "reservedEndAt",
                "customerId",
                "customerName",
                "customerNickname",
                "phoneE164",
                "note",
                "tableId",
                "tableGroupId"
            );
    }

    @Test
    void mapsApplicationErrorsToApiErrors() throws Exception {
        assertApplicationError(ReservationCreateError.STORE_NOT_FOUND, 404, "STORE_NOT_FOUND");
        assertApplicationError(ReservationCreateError.STORE_SCOPE_MISMATCH, 403, "STORE_SCOPE_MISMATCH");
        assertApplicationError(ReservationCreateError.STORE_ACCESS_DENIED, 403, "FORBIDDEN");
        assertApplicationError(ReservationCreateError.CUSTOMER_NOT_FOUND, 404, "CUSTOMER_NOT_FOUND");
        assertApplicationError(ReservationCreateError.RESERVATION_DUPLICATE_ACTIVE, 409, "RESERVATION_DUPLICATE_ACTIVE");
        assertApplicationError(ReservationCreateError.RESERVATION_CAPACITY_INSUFFICIENT, 409, "RESERVATION_CAPACITY_INSUFFICIENT");
        assertApplicationError(ReservationCreateError.RESERVATION_CODE_CONFLICT, 409, "RESERVATION_CODE_CONFLICT");
        assertApplicationError(ReservationCreateError.COMMAND_IN_PROGRESS, 409, "IDEMPOTENCY_IN_PROGRESS");
        assertApplicationError(ReservationCreateError.FAILED_IDEMPOTENCY_REQUIRES_NEW_KEY, 409, "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY");
        assertApplicationError(ReservationCreateError.IDEMPOTENCY_CONFLICT, 409, "IDEMPOTENCY_CONFLICT");
        assertApplicationError(ReservationCreateError.AUDIT_WRITE_FAILED, 500, "AUDIT_WRITE_FAILED");
        assertApplicationError(ReservationCreateError.BUSINESS_EVENT_WRITE_FAILED, 500, "EVENT_WRITE_FAILED");
        assertApplicationError(ReservationCreateError.STATE_TRANSITION_WRITE_FAILED, 500, "STATE_TRANSITION_WRITE_FAILED");
        assertApplicationError(ReservationCreateError.REPOSITORY_SAVE_FAILED, 500, "PERSISTENCE_ERROR");
    }

    @Test
    void forbiddenRoleReturnsForbiddenWithoutCallingService() throws Exception {
        actorProvider.actor = CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "customer",
            Set.of("customer"),
            Set.of("reservation.create"),
            Set.of(STORE_ID)
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
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
            Set.of(),
            Set.of(STORE_ID)
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-no-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
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
                .content(validRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void noForbiddenReservationApiOrUiArtifactsAreCreated() throws Exception {
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
            .noneMatch(ReservationControllerTest::isForbiddenQueueApiFile);
        assertThat(sourceFiles)
            .noneMatch(path -> path.contains("/turnover/api/"));
        assertThat(sourceFiles)
            .noneMatch(path -> path.endsWith("/CheckInController.java"));
        assertThat(sourceFiles)
            .noneMatch(path -> path.endsWith("/ReservationCancellationController.java"));
        assertThat(sourceFiles)
            .noneMatch(path -> path.endsWith("/ReservationNoShowController.java"));

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
                "src/pages/ReservationArrivedDirectSeatingPage.vue",
                "src/pages/ReservationArrivedToQueuePage.vue",
                "src/pages/ReservationCheckInPage.vue",
                "src/pages/ReservationTodayViewPage.vue",
                "src/components/reservation-workbench/CreateReservationDialog.vue",
                "src/components/reservation-workbench/ReservationMonthCalendar.vue",
                "src/components/reservation-workbench/ReservationQuickActionPanel.vue",
                "src/components/reservation-workbench/ReservationSeatDialog.vue",
                "src/components/reservation-workbench/ReservationTableSwitchDialog.vue",
                "src/components/reservation-workbench/ReservationTodayListItem.vue",
                "src/components/reservation-workbench/ReservationTodayListPanel.vue",
                "src/pages/SeatingFromCalledQueuePage.vue"
            ).contains(path))
            .noneMatch(path -> path.toLowerCase().contains("reservation"));
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

    private void assertApplicationError(ReservationCreateError applicationError, int expectedStatus, String expectedApiCode) throws Exception {
        when(applicationService.createReservation(any())).thenReturn(ReservationCreateResult.failure(applicationError));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-" + applicationError.name())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequestJson()))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(expectedApiCode))
            .andExpect(jsonPath("$.error.messageKey").value(messageKey(expectedApiCode)))
            .andExpect(jsonPath("$.error.details").isMap());
    }

    private static ReservationCreateResult success(boolean replayed) {
        if (replayed) {
            return ReservationCreateResult.replay(
                RESERVATION_ID,
                CUSTOMER_ID,
                "R-20300620-0007",
                4,
                LocalDate.parse("2030-06-20"),
                RESERVED_START_AT,
                RESERVED_END_AT,
                HOLD_UNTIL_AT,
                "confirmed"
            );
        }
        return ReservationCreateResult.success(
            RESERVATION_ID,
            CUSTOMER_ID,
            "R-20300620-0007",
            4,
            LocalDate.parse("2030-06-20"),
            RESERVED_START_AT,
            RESERVED_END_AT,
            HOLD_UNTIL_AT,
            "confirmed",
            "completed",
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            UUID.randomUUID()
        );
    }

    private static String validRequestJson() {
        return """
            {
              "partySize": 4,
              "reservedStartAt": "2030-06-20T11:00:00Z",
              "reservedEndAt": "2030-06-20T12:30:00Z",
              "customerId": null,
              "customerName": "Guest",
              "customerNickname": null,
              "phoneE164": "+6591234567",
              "note": null,
              "tableId": null,
              "tableGroupId": null
            }
            """;
    }

    private static String messageKey(String apiCode) {
        return switch (apiCode) {
            case "RESERVATION_DUPLICATE_ACTIVE" -> "reservation.duplicate_active";
            case "RESERVATION_CAPACITY_INSUFFICIENT" -> "reservation.capacity_insufficient";
            case "RESERVATION_CODE_CONFLICT" -> "reservation.code_conflict";
            case "RESERVATION_START_IN_PAST" -> "reservation.start_in_past";
            case "EVENT_WRITE_FAILED" -> "reservation.event_write_failed";
            default -> "reservation." + apiCode.toLowerCase();
        };
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
