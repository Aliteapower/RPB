package com.rpb.reservation.reservation.integration;

import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.ACTOR_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.BUSINESS_DATE;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.CAPACITY_CUSTOMER_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.DUPLICATE_CUSTOMER_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.END_AT;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.EXISTING_CUSTOMER_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.HOLD_UNTIL_AT;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.OTHER_STORE_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.REPLAY_RESERVATION_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.START_AT;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.STORE_ID;
import static com.rpb.reservation.reservation.integration.ReservationCreateIntegrationFixture.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.reservation.api.CreateReservationRequest;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReservationCreateApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private ReservationCreateIntegrationFixture fixture;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @BeforeEach
    void setUp() {
        fixture = new ReservationCreateIntegrationFixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("reservation.create")));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void createsReservationWithExistingCustomerThroughFullApiToPostgresPath() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-existing-customer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Existing Guest")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").isNotEmpty())
            .andExpect(jsonPath("$.reservationCode").isNotEmpty())
            .andExpect(jsonPath("$.status").value("confirmed"))
            .andExpect(jsonPath("$.partySize").value(4))
            .andExpect(jsonPath("$.reservedStartAt").value(START_AT.toString()))
            .andExpect(jsonPath("$.reservedEndAt").value(END_AT.toString()))
            .andExpect(jsonPath("$.holdUntilAt").value(HOLD_UNTIL_AT.toString()))
            .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
            .andExpect(jsonPath("$.customer.id").value(EXISTING_CUSTOMER_ID.toString()))
            .andExpect(jsonPath("$.events[0]").value("reservation.created"))
            .andExpect(jsonPath("$.events[1]").value("reservation.confirmed"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulReservationDatabaseState("reservation-existing-customer", EXISTING_CUSTOMER_ID, 4, START_AT, END_AT);
    }

    @Test
    void createsReservationUsingPhoneCustomerThroughPostgresPath() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-phone-customer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 2,
                      "reservedStartAt": "%s",
                      "reservedEndAt": "%s",
                      "customerId": null,
                      "customerName": "Phone Guest",
                      "customerNickname": null,
                      "phoneE164": "+6591234567",
                      "note": null
                    }
                    """.formatted(START_AT, END_AT)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.customer.phoneE164").value("+6591234567"));

        assertThat(fixture.countWhere("select count(*) from customers where phone_e164 = '+6591234567'")).isEqualTo(1);
        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void createsNoPhoneTemporaryReservationWithoutQueueOrSeatingSideEffects() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-no-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 2,
                      "reservedStartAt": "%s",
                      "reservedEndAt": "%s",
                      "customerId": null,
                      "customerName": "No Phone Guest",
                      "customerNickname": "VIP friend",
                      "phoneE164": null,
                      "note": "Window seat"
                    }
                    """.formatted(START_AT, END_AT)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("confirmed"));

        assertThat(fixture.countWhere("select count(*) from customers where phone_e164 is null")).isEqualTo(4);
        assertThat(fixture.countWhere("""
            select count(*) from customers
            where customer_type = 'temporary'
              and phone_e164 is null
            """)).isEqualTo(1);
        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void derivesReservedEndAtWhenOmitted() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-derived-end")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize": 3,
                      "reservedStartAt": "%s",
                      "reservedEndAt": null,
                      "customerId": "%s",
                      "customerName": null,
                      "customerNickname": null,
                      "phoneE164": null,
                      "note": null
                    }
                    """.formatted(START_AT, EXISTING_CUSTOMER_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservedEndAt").value(END_AT.toString()));

        assertThat(fixture.scalarOffsetDateTime("select reserved_end_at from reservations"))
            .isEqualTo(OffsetDateTime.ofInstant(END_AT, ZoneOffset.UTC));
    }

    @Test
    void completedIdempotencyReplayDoesNotCreateDuplicateReservationOrEvents() throws Exception {
        MvcResult first = mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Replay Guest")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotency.replayed").value(false))
            .andReturn();

        String reservationId = objectMapper.readTree(first.getResponse().getContentAsString()).path("reservationId").asText();

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Replay Guest")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationId").value(reservationId))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type in ('reservation.created', 'reservation.confirmed')"))
            .isEqualTo(2);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(1);
        assertThat(fixture.count("audit_logs")).isEqualTo(1);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void rejectsMissingIdempotencyKeyBeforeMutation() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Missing Key")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void rejectsInvalidPartySizeBeforeMutation() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-invalid-party")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"partySize":0,"reservedStartAt":"%s","reservedEndAt":"%s"}
                    """.formatted(START_AT, END_AT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARTY_SIZE"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void rejectsInvalidTimeRangeBeforeMutation() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-invalid-range")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"partySize":2,"reservedStartAt":"%s","reservedEndAt":"%s"}
                    """.formatted(END_AT, START_AT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_TIME_RANGE"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void rejectsPastReservationStartAndMarksIdempotencyFailed() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-past")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "partySize":2,
                      "reservedStartAt":"2020-06-20T03:00:00Z",
                      "reservedEndAt":"2020-06-20T04:30:00Z",
                      "customerId":"%s"
                    }
                    """.formatted(EXISTING_CUSTOMER_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_START_IN_PAST"));

        assertApplicationFailure("reservation-past");
    }

    @Test
    void rejectsInvalidPhoneBeforeMutation() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-invalid-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"partySize":2,"reservedStartAt":"%s","reservedEndAt":"%s","phoneE164":"91234567"}
                    """.formatted(START_AT, END_AT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PHONE_E164"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void rejectsMissingCustomerAndMarksIdempotencyFailed() throws Exception {
        UUID missingCustomerId = UUID.fromString("40000000-0000-0000-0000-000000000599");

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-missing-customer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest(missingCustomerId, "Missing Customer")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));

        assertApplicationFailure("reservation-missing-customer");
    }

    @Test
    void rejectsDuplicateActiveReservation() throws Exception {
        fixture.activeReservation(
            UUID.fromString("50000000-0000-0000-0000-000000000501"),
            DUPLICATE_CUSTOMER_ID,
            "R-DUPLICATE",
            2
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-duplicate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest(DUPLICATE_CUSTOMER_ID, "Duplicate Guest")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_DUPLICATE_ACTIVE"));

        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertApplicationFailure("reservation-duplicate");
    }

    @Test
    void rejectsCapacityInsufficientUsingV1FallbackLimit() throws Exception {
        fixture.activeReservation(
            UUID.fromString("50000000-0000-0000-0000-000000000502"),
            CAPACITY_CUSTOMER_ID,
            "R-CAPACITY",
            49
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-capacity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest(EXISTING_CUSTOMER_ID, 2, "Capacity Guest")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_CAPACITY_INSUFFICIENT"));

        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertApplicationFailure("reservation-capacity");
    }

    @Test
    void idempotencyInProgressDoesNotMutateDatabase() throws Exception {
        CreateReservationRequest request = existingCustomerRequestObject(EXISTING_CUSTOMER_ID, 4, "In Progress Guest");
        fixture.idempotencyRecord("reservation-in-progress", hash("reservation-in-progress", request), "started", null, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-in-progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_IN_PROGRESS"))
            .andExpect(jsonPath("$.idempotency.status").value("started"));

        assertThat(fixture.count("reservations")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void failedIdempotencyRequiresNewKey() throws Exception {
        CreateReservationRequest request = existingCustomerRequestObject(EXISTING_CUSTOMER_ID, 4, "Failed Key Guest");
        fixture.idempotencyRecord("reservation-failed", hash("reservation-failed", request), "failed", null, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        assertThat(fixture.count("reservations")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void idempotencyHashConflictDoesNotMutateDatabase() throws Exception {
        fixture.idempotencyRecord(
            "reservation-hash-conflict",
            "different-hash",
            "completed",
            REPLAY_RESERVATION_ID,
            EXISTING_CUSTOMER_ID
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-hash-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Hash Conflict Guest")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"))
            .andExpect(jsonPath("$.idempotency.status").value("conflict"));

        assertThat(fixture.count("reservations")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void allowedRolesCanCreateReservation() throws Exception {
        for (String role : Set.of("tenant_admin", "store_manager", "store_staff")) {
            fixture.reset();
            fixture.createBaseStore();
            actorProvider.set(actor(Set.of(STORE_ID), Set.of(role), Set.of("reservation.create")));

            mockMvc.perform(post(ENDPOINT, STORE_ID)
                    .header("Idempotency-Key", "reservation-role-" + role)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(existingCustomerRequest(role + " Guest")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    void appGateRuntimeAllowsEnabledTenantStoreAndPermittedActor() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "appgate-runtime-allowed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("App Gate Allowed")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertThat(fixture.count("app_gate_audit_logs")).isEqualTo(0);
    }

    @Test
    void appGateRuntimeRejectsTenantWithoutReservationQueueEntitlementAndAuditsDenial() throws Exception {
        jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "appgate-runtime-tenant-disabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Tenant Disabled")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("TENANT_APP_NOT_ENABLED"));

        assertNoMutationBeforeApplicationService();
        assertAppGateDenialAudit("TENANT_APP_NOT_ENABLED", "reservation.create");
    }

    @Test
    void appGateRuntimeRejectsStoreWithoutEnabledReservationQueueAndAuditsDenial() throws Exception {
        jdbc.update("""
            update store_app_settings
            set is_enabled = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "appgate-runtime-store-disabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Store Disabled")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STORE_APP_NOT_ENABLED"));

        assertNoMutationBeforeApplicationService();
        assertAppGateDenialAudit("STORE_APP_NOT_ENABLED", "reservation.create");
    }

    @Test
    void appGateRuntimeRejectsActorWithoutReservationPermissionAndAuditsDenial() throws Exception {
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of()));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "appgate-runtime-permission-denied")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Permission Denied")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        assertNoMutationBeforeApplicationService();
        assertAppGateDenialAudit("PERMISSION_DENIED", "reservation.create");
    }

    @Test
    void meAppsRuntimeReturnsOnlyVisibleEnabledReservationQueueEntryForStore() throws Exception {
        mockMvc.perform(get("/api/me/apps").param("storeId", STORE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.apps[0].appKey").value("reservation_queue"))
            .andExpect(jsonPath("$.apps[0].appName").value("订位排号系统"))
            .andExpect(jsonPath("$.apps[0].entryRoute").value("/stores/" + STORE_ID + "/staff"))
            .andExpect(jsonPath("$.apps[0].entryVisible").value(true))
            .andExpect(jsonPath("$.apps[0].permissions[0]").exists());

        jdbc.update("""
            update store_app_settings
            set entry_visible = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);

        mockMvc.perform(get("/api/me/apps").param("storeId", STORE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.apps").isEmpty());

        jdbc.update("""
            update store_app_settings
            set entry_visible = true,
                is_enabled = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);

        mockMvc.perform(get("/api/me/apps").param("storeId", STORE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.apps").isEmpty());
    }

    @Test
    void forbiddenRoleMissingPermissionAndStoreScopeMismatchRejectBeforeApplicationMutation() throws Exception {
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("customer"), Set.of("reservation.create")));
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Forbidden")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of()));
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "reservation-missing-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("No Permission")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("reservation.create")));
        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID)
                .header("Idempotency-Key", "reservation-store-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(existingCustomerRequest("Scope")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void boundaryArtifactsRemainLimitedToReservationCreateApi() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java", "com", "rpb", "reservation"))) {
            assertThat(paths.filter(Files::isRegularFile).map(path -> path.toString().replace('\\', '/')).toList())
                .noneMatch(ReservationCreateApiIntegrationTest::isForbiddenQueueApiFile)
                .noneMatch(path -> path.contains("/turnover/api/"))
                .noneMatch(path -> path.contains("/seating/api/"))
                .noneMatch(path -> path.toLowerCase().contains("checkincontroller"))
                .noneMatch(path -> path.toLowerCase().contains("reservationnoshowcontroller"))
                .noneMatch(path -> path.toLowerCase().contains("reservationcancellationcontroller"))
                .noneMatch(path -> path.toLowerCase().contains("tableassignmentcontroller"));
        }
        try (Stream<Path> paths = Files.walk(Path.of("."))) {
            assertThat(paths.filter(Files::isRegularFile).map(path -> path.toString().replace('\\', '/')).toList())
                .noneMatch(path -> path.endsWith(".vue")
                    && path.toLowerCase().contains("reservation")
                    && !path.endsWith("src/pages/ReservationCreatePage.vue")
                    && !path.endsWith("src/pages/ReservationCheckInPage.vue")
                    && !path.endsWith("src/pages/ReservationArrivedDirectSeatingPage.vue")
                    && !path.endsWith("src/pages/ReservationArrivedToQueuePage.vue")
                    && !path.endsWith("src/pages/ReservationTodayViewPage.vue"))
                .noneMatch(path -> path.toLowerCase().contains("openapi") && (
                    path.endsWith(".yml") || path.endsWith(".yaml") || path.endsWith(".json")
                ));
        }
    }

    private static boolean isForbiddenQueueApiFile(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
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

    private void assertSuccessfulReservationDatabaseState(
        String idempotencyKey,
        UUID expectedCustomerId,
        int expectedPartySize,
        Instant expectedStartAt,
        Instant expectedEndAt
    ) {
        assertThat(fixture.count("reservations")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from reservations")).isEqualTo("confirmed");
        assertThat(fixture.scalarString("select reservation_code from reservations")).isNotBlank();
        assertThat(fixture.scalarInteger("select party_size from reservations")).isEqualTo(expectedPartySize);
        assertThat(fixture.scalarString("select customer_id::text from reservations")).isEqualTo(expectedCustomerId.toString());
        assertThat(fixture.scalarOffsetDateTime("select reserved_start_at from reservations"))
            .isEqualTo(OffsetDateTime.ofInstant(expectedStartAt, ZoneOffset.UTC));
        assertThat(fixture.scalarOffsetDateTime("select reserved_end_at from reservations"))
            .isEqualTo(OffsetDateTime.ofInstant(expectedEndAt, ZoneOffset.UTC));
        assertThat(fixture.scalarOffsetDateTime("select hold_until_at from reservations")).isNotNull();
        assertThat(fixture.scalarString("select business_date::text from reservations")).isEqualTo(BUSINESS_DATE.toString());
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.created'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.confirmed'")).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where target_type = 'reservation' and from_status = 'none'
              and to_status = 'confirmed' and transition_code = 'reservation.confirm'
            """)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from audit_logs
            where operation_code = 'reservation.create' and target_type = 'reservation'
            """)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertBoundaryTablesRemainEmpty();
    }

    private void assertApplicationFailure(String idempotencyKey) {
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("failed");
        assertThat(fixture.countWhere("select count(*) from business_events where event_type like 'reservation.%'")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    private void assertNoMutationBeforeApplicationService() {
        assertThat(fixture.count("reservations")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertBoundaryTablesRemainEmpty();
    }

    private void assertAppGateDenialAudit(String expectedReason, String expectedPermission) {
        assertThat(fixture.countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and operator_user_id = ?
              and operator_role = 'staff'
              and after_json ->> 'denyReason' = ?
              and after_json ->> 'requiredPermission' = ?
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason, expectedPermission)).isEqualTo(1);
    }

    private void assertBoundaryTablesRemainEmpty() {
        assertThat(fixture.countWhere("select count(*) from reservations where status in ('no_show', 'cancelled')")).isEqualTo(0);
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("table_locks")).isEqualTo(0);
        assertThat(fixture.count("reservation_preassignments")).isEqualTo(0);
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
    }

    private String existingCustomerRequest(String customerName) {
        return existingCustomerRequest(EXISTING_CUSTOMER_ID, 4, customerName);
    }

    private String existingCustomerRequest(UUID customerId, String customerName) {
        return existingCustomerRequest(customerId, 4, customerName);
    }

    private String existingCustomerRequest(UUID customerId, int partySize, String customerName) {
        return json(existingCustomerRequestObject(customerId, partySize, customerName));
    }

    private CreateReservationRequest existingCustomerRequestObject(UUID customerId, int partySize, String customerName) {
        return new CreateReservationRequest(
            partySize,
            START_AT,
            END_AT,
            customerId,
            customerName,
            null,
            null,
            null
        );
    }

    private String json(CreateReservationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception exception) {
            throw new IllegalStateException("reservation_request_json_failed", exception);
        }
    }

    private String hash(String idempotencyKey, CreateReservationRequest request) {
        return ReservationCreateApplicationService.requestHash(new CreateReservationCommand(
            TENANT_ID,
            STORE_ID,
            request.partySize(),
            request.reservedStartAt(),
            request.reservedEndAt(),
            request.customerId(),
            request.customerName(),
            request.customerNickname(),
            request.phoneE164(),
            request.note(),
            idempotencyKey,
            ACTOR_ID,
            "staff",
            null,
            "staff",
            null
        ));
    }

    private static CurrentActor actor(Set<UUID> storeIds, Set<String> roles, Set<String> permissions) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            roles.contains("customer") ? "customer" : "staff",
            roles,
            permissions,
            storeIds
        );
    }

    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        @Primary
        TestCurrentActorProvider testCurrentActorProvider() {
            return new TestCurrentActorProvider();
        }
    }
}
