package com.rpb.reservation.cleaning.integration;

import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.ACTOR_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.AVAILABLE_TABLE_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.CLEANING_TABLE_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.GROUP_CLEANING_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.GROUP_MEMBER_TABLE_1_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.GROUP_MEMBER_TABLE_2_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.GROUP_SEATING_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.INVALID_TABLE_GROUP_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.MISSING_RESOURCE_SEATING_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.OTHER_STORE_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.STORE_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.TABLE_CLEANING_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.TABLE_GROUP_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.TABLE_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.TABLE_SEATING_ID;
import static com.rpb.reservation.cleaning.integration.CleaningCompleteIntegrationFixture.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.cleaning.api.CompleteCleaningRequest;
import com.rpb.reservation.cleaning.api.StartCleaningRequest;
import com.rpb.reservation.cleaning.application.command.CompleteCleaningCommand;
import com.rpb.reservation.cleaning.application.command.StartCleaningCommand;
import com.rpb.reservation.cleaning.application.service.CleaningApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.file.Files;
import java.nio.file.Path;
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
class CleaningCompleteApiIntegrationTest {
    private static final String START_ENDPOINT = "/api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start";
    private static final String COMPLETE_ENDPOINT = "/api/v1/stores/{storeId}/cleanings/{cleaningId}/complete";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private CleaningCompleteIntegrationFixture fixture;

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
        fixture = new CleaningCompleteIntegrationFixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("cleaning.start", "cleaning.complete")));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void startsCleaningFromSeatingIdWithDiningTableThroughFullApiToPostgresPath() throws Exception {
        fixture.createOccupiedTableSeating(TABLE_SEATING_ID, TABLE_ID);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.seatingId").value(TABLE_SEATING_ID.toString()))
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.cleaningStatus").value("cleaning"))
            .andExpect(jsonPath("$.tableStatus").value("cleaning"))
            .andExpect(jsonPath("$.events[0]").value("cleaning.started"))
            .andExpect(jsonPath("$.events[1]").value("table.cleaning"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertStartTableDatabaseState("start-table", TABLE_ID);
    }

    @Test
    void startThenCompleteCleaningWithReturnedCleaningIdThroughFullApiToPostgresPath() throws Exception {
        fixture.createOccupiedTableSeating(TABLE_SEATING_ID, TABLE_ID);

        MvcResult startResult = mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "closed-loop-start-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.cleaningId").isNotEmpty())
            .andExpect(jsonPath("$.cleaningStatus").value("cleaning"))
            .andExpect(jsonPath("$.tableStatus").value("cleaning"))
            .andReturn();

        UUID returnedCleaningId = UUID.fromString(
            objectMapper.readTree(startResult.getResponse().getContentAsString()).path("cleaningId").asText()
        );

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, returnedCleaningId)
                .header("Idempotency-Key", "closed-loop-complete-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.cleaningId").value(returnedCleaningId.toString()))
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.cleaningStatus").value("released"))
            .andExpect(jsonPath("$.tableStatus").value("available"))
            .andExpect(jsonPath("$.events[0]").value("cleaning.completed"))
            .andExpect(jsonPath("$.events[1]").value("table.available"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertClosedLoopDatabaseState(returnedCleaningId);
    }

    @Test
    void startsCleaningFromSeatingIdWithTableGroupThroughFullApiToPostgresPath() throws Exception {
        fixture.createOccupiedTableGroupSeating(GROUP_SEATING_ID, TABLE_GROUP_ID);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, GROUP_SEATING_ID)
                .header("Idempotency-Key", "start-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.type").value("TABLE_GROUP"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_GROUP_ID.toString()))
            .andExpect(jsonPath("$.cleaningStatus").value("cleaning"))
            .andExpect(jsonPath("$.tableStatus").value("cleaning"));

        assertThat(fixture.scalarString("select status from dining_tables where id = ?", GROUP_MEMBER_TABLE_1_ID))
            .isEqualTo("cleaning");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", GROUP_MEMBER_TABLE_2_ID))
            .isEqualTo("cleaning");
        assertThat(fixture.countWhere("""
            select count(*) from cleanings
            where seating_id = ? and resource_type = 'table_group'
              and table_group_id = ? and table_id is null and status = 'cleaning'
            """, GROUP_SEATING_ID, TABLE_GROUP_ID)).isEqualTo(1);
        assertStartCommonDatabaseState("start-group");
    }

    @Test
    void startCleaningCompletedIdempotencyReplayDoesNotMutateDatabase() throws Exception {
        StartCleaningRequest request = startRequest();
        UUID replayCleaningId = UUID.fromString("80000000-0000-0000-0000-000000000299");
        fixture.idempotencyRecord(
            "start_cleaning",
            "start-replay",
            startHash(TABLE_SEATING_ID, request),
            "completed",
            replayCleaningId,
            TABLE_SEATING_ID,
            "dining_table",
            TABLE_ID,
            "cleaning",
            "cleaning"
        );

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cleaningId").value(replayCleaningId.toString()))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void startCleaningRejectsMissingIdempotencyKeyBeforeMutation() throws Exception {
        fixture.createOccupiedTableSeating(TABLE_SEATING_ID, TABLE_ID);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        assertNoCleaningMutationBeforeApplicationService();
    }

    @Test
    void startCleaningRejectsMissingSeatingAndMarksIdempotencyFailed() throws Exception {
        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-missing-seating")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("SEATING_NOT_FOUND"));

        assertApplicationFailure("start-missing-seating");
    }

    @Test
    void startCleaningRejectsMissingSeatingResource() throws Exception {
        fixture.createSeatingWithoutResource(MISSING_RESOURCE_SEATING_ID);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, MISSING_RESOURCE_SEATING_ID)
                .header("Idempotency-Key", "start-missing-resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SEATING_RESOURCE_NOT_FOUND"));

        assertApplicationFailure("start-missing-resource");
    }

    @Test
    void startCleaningRejectsTableThatIsNotOccupied() throws Exception {
        fixture.createOccupiedTableSeating(TABLE_SEATING_ID, AVAILABLE_TABLE_ID);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-table-not-occupied")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_NOT_OCCUPIED"));

        assertApplicationFailure("start-table-not-occupied");
    }

    @Test
    void startCleaningRejectsInvalidTableGroup() throws Exception {
        fixture.createOccupiedTableGroupSeating(GROUP_SEATING_ID, INVALID_TABLE_GROUP_ID);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, GROUP_SEATING_ID)
                .header("Idempotency-Key", "start-invalid-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_GROUP_INVALID"));

        assertApplicationFailure("start-invalid-group");
    }

    @Test
    void startCleaningRejectsAlreadyActiveCleaning() throws Exception {
        fixture.createOccupiedTableSeating(TABLE_SEATING_ID, TABLE_ID);
        fixture.createActiveCleaningForResource(TABLE_CLEANING_ID, TABLE_SEATING_ID, TABLE_ID);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-already-active")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CLEANING_ALREADY_ACTIVE"));

        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "start-already-active"))
            .isEqualTo("failed");
        assertThat(fixture.count("cleanings")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void startCleaningIdempotencyInProgressDoesNotMutateDatabase() throws Exception {
        StartCleaningRequest request = startRequest();
        fixture.idempotencyRecord("start_cleaning", "start-in-progress", startHash(TABLE_SEATING_ID, request), "started", null, null, null, null, null, null);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-in-progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_IN_PROGRESS"))
            .andExpect(jsonPath("$.idempotency.status").value("started"));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void startCleaningFailedIdempotencyRequiresNewKey() throws Exception {
        StartCleaningRequest request = startRequest();
        fixture.idempotencyRecord("start_cleaning", "start-failed", startHash(TABLE_SEATING_ID, request), "failed", null, null, null, null, null, null);

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY"));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void startCleaningIdempotencyHashConflictDoesNotMutateDatabase() throws Exception {
        fixture.idempotencyRecord("start_cleaning", "start-hash-conflict", "different-hash", "completed", TABLE_CLEANING_ID, TABLE_SEATING_ID, "dining_table", TABLE_ID, "cleaning", "cleaning");

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-hash-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void allowedCleaningRolesCanStartCleaning() throws Exception {
        for (String role : Set.of("tenant_admin", "store_manager", "store_staff")) {
            fixture.reset();
            fixture.createBaseStore();
            fixture.createOccupiedTableSeating(TABLE_SEATING_ID, TABLE_ID);
            actorProvider.set(actor(Set.of(STORE_ID), Set.of(role), Set.of("cleaning.start")));

            mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                    .header("Idempotency-Key", "start-role-" + role)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(startRequest())))
                .andExpect(status().isCreated());
        }
    }

    @Test
    void forbiddenRoleAndMissingPermissionRejectStartCleaningBeforeApplicationMutation() throws Exception {
        fixture.createOccupiedTableSeating(TABLE_SEATING_ID, TABLE_ID);
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("customer"), Set.of("cleaning.start")));

        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertNoCleaningMutationBeforeApplicationService();

        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of()));
        mockMvc.perform(post(START_ENDPOINT, STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-missing-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        assertNoCleaningMutationBeforeApplicationService();
    }

    @Test
    void storeScopeMismatchRejectsStartCleaningBeforeApplicationMutation() throws Exception {
        fixture.createOccupiedTableSeating(TABLE_SEATING_ID, TABLE_ID);
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("cleaning.start")));

        mockMvc.perform(post(START_ENDPOINT, OTHER_STORE_ID, TABLE_SEATING_ID)
                .header("Idempotency-Key", "start-store-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(startRequest())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));

        assertNoCleaningMutationBeforeApplicationService();
    }

    @Test
    void completesCleaningByCleaningIdWithDiningTableThroughFullApiToPostgresPath() throws Exception {
        fixture.createActiveTableCleaning(TABLE_CLEANING_ID, TABLE_SEATING_ID, CLEANING_TABLE_ID);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.cleaningId").value(TABLE_CLEANING_ID.toString()))
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(CLEANING_TABLE_ID.toString()))
            .andExpect(jsonPath("$.cleaningStatus").value("released"))
            .andExpect(jsonPath("$.tableStatus").value("available"))
            .andExpect(jsonPath("$.events[0]").value("cleaning.completed"))
            .andExpect(jsonPath("$.events[1]").value("table.available"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertCompleteTableDatabaseState("complete-table", TABLE_CLEANING_ID, CLEANING_TABLE_ID);
    }

    @Test
    void completesCleaningByCleaningIdWithTableGroupThroughFullApiToPostgresPath() throws Exception {
        fixture.createActiveTableGroupCleaning(GROUP_CLEANING_ID, GROUP_SEATING_ID, TABLE_GROUP_ID);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, GROUP_CLEANING_ID)
                .header("Idempotency-Key", "complete-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resource.type").value("TABLE_GROUP"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_GROUP_ID.toString()))
            .andExpect(jsonPath("$.cleaningStatus").value("released"))
            .andExpect(jsonPath("$.tableStatus").value("available"));

        assertThat(fixture.scalarString("select status from dining_tables where id = ?", GROUP_MEMBER_TABLE_1_ID))
            .isEqualTo("available");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", GROUP_MEMBER_TABLE_2_ID))
            .isEqualTo("available");
        assertCompleteCommonDatabaseState("complete-group", GROUP_CLEANING_ID);
    }

    @Test
    void completeCleaningCompletedIdempotencyReplayDoesNotMutateDatabase() throws Exception {
        CompleteCleaningRequest request = completeRequest();
        fixture.idempotencyRecord(
            "complete_cleaning",
            "complete-replay",
            completeHash(TABLE_CLEANING_ID, request),
            "completed",
            TABLE_CLEANING_ID,
            TABLE_SEATING_ID,
            "dining_table",
            CLEANING_TABLE_ID,
            "available",
            "released"
        );

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cleaningId").value(TABLE_CLEANING_ID.toString()))
            .andExpect(jsonPath("$.tableStatus").value("available"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void completeCleaningRejectsMissingIdempotencyKeyBeforeMutation() throws Exception {
        fixture.createActiveTableCleaning(TABLE_CLEANING_ID, TABLE_SEATING_ID, CLEANING_TABLE_ID);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        assertThat(fixture.scalarString("select status from cleanings where id = ?", TABLE_CLEANING_ID))
            .isEqualTo("cleaning");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", CLEANING_TABLE_ID))
            .isEqualTo("cleaning");
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
    }

    @Test
    void completeCleaningRejectsMissingCleaningAndMarksIdempotencyFailed() throws Exception {
        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-missing-cleaning")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("CLEANING_NOT_FOUND"));

        assertApplicationFailure("complete-missing-cleaning");
    }

    @Test
    void completeCleaningRejectsAlreadyReleasedCleaning() throws Exception {
        fixture.createReleasedCleaning(TABLE_CLEANING_ID, TABLE_SEATING_ID, TABLE_ID);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-already-released")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CLEANING_ALREADY_COMPLETED"));

        assertApplicationFailure("complete-already-released");
    }

    @Test
    void completeCleaningRejectsTableThatIsNotCleaning() throws Exception {
        fixture.createActiveTableCleaning(TABLE_CLEANING_ID, TABLE_SEATING_ID, CLEANING_TABLE_ID);
        jdbc.update("update dining_tables set status = 'occupied' where id = ?", CLEANING_TABLE_ID);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-table-not-cleaning")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_NOT_CLEANING"));

        assertApplicationFailure("complete-table-not-cleaning");
    }

    @Test
    void completeCleaningRejectsInvalidTableGroup() throws Exception {
        fixture.createInvalidTableGroupCleaning(GROUP_CLEANING_ID, GROUP_SEATING_ID);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, GROUP_CLEANING_ID)
                .header("Idempotency-Key", "complete-invalid-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_GROUP_INVALID"));

        assertApplicationFailure("complete-invalid-group");
    }

    @Test
    void completeCleaningIdempotencyInProgressDoesNotMutateDatabase() throws Exception {
        CompleteCleaningRequest request = completeRequest();
        fixture.idempotencyRecord("complete_cleaning", "complete-in-progress", completeHash(TABLE_CLEANING_ID, request), "started", null, null, null, null, null, null);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-in-progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_IN_PROGRESS"))
            .andExpect(jsonPath("$.idempotency.status").value("started"));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void completeCleaningFailedIdempotencyRequiresNewKey() throws Exception {
        CompleteCleaningRequest request = completeRequest();
        fixture.idempotencyRecord("complete_cleaning", "complete-failed", completeHash(TABLE_CLEANING_ID, request), "failed", null, null, null, null, null, null);

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY"));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void completeCleaningIdempotencyHashConflictDoesNotMutateDatabase() throws Exception {
        fixture.idempotencyRecord("complete_cleaning", "complete-hash-conflict", "different-hash", "completed", TABLE_CLEANING_ID, TABLE_SEATING_ID, "dining_table", CLEANING_TABLE_ID, "available", "released");

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-hash-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void forbiddenRoleMissingPermissionAndStoreScopeMismatchRejectCompleteCleaningBeforeApplicationMutation() throws Exception {
        fixture.createActiveTableCleaning(TABLE_CLEANING_ID, TABLE_SEATING_ID, CLEANING_TABLE_ID);
        actorProvider.set(actor(Set.of(STORE_ID), Set.of("customer"), Set.of("cleaning.complete")));

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-forbidden-role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of()));
        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-missing-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        actorProvider.set(actor(Set.of(STORE_ID), Set.of("store_staff"), Set.of("cleaning.complete")));
        mockMvc.perform(post(COMPLETE_ENDPOINT, OTHER_STORE_ID, TABLE_CLEANING_ID)
                .header("Idempotency-Key", "complete-store-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(completeRequest())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));

        assertThat(fixture.scalarString("select status from cleanings where id = ?", TABLE_CLEANING_ID))
            .isEqualTo("cleaning");
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
    }

    @Test
    void boundaryArtifactsRemainLimitedToWalkInAndCleaning() throws Exception {
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java", "com", "rpb", "reservation"))) {
            assertThat(paths.filter(Files::isRegularFile).map(path -> path.toString().replace('\\', '/')).toList())
                .noneMatch(CleaningCompleteApiIntegrationTest::isForbiddenQueueApiFile)
                .noneMatch(path -> path.contains("\\turnover\\api\\") || path.contains("/turnover/api/"))
                .noneMatch(path -> path.contains("\\seating\\api\\") || path.contains("/seating/api/"))
                .noneMatch(path -> path.toLowerCase().contains("checkincontroller")
                    || path.toLowerCase().contains("check_incontroller")
                    || path.toLowerCase().contains("\\checkin\\api\\")
                    || path.toLowerCase().contains("/checkin/api/"))
                .noneMatch(path -> path.toLowerCase().contains("noshowcontroller")
                    || path.toLowerCase().contains("no_showcontroller")
                    || path.toLowerCase().contains("\\noshow\\api\\")
                    || path.toLowerCase().contains("/noshow/api/"))
                .noneMatch(path -> path.toLowerCase().contains("cancellationcontroller")
                    || path.toLowerCase().contains("cancelreservationcontroller"))
                .noneMatch(path -> path.toLowerCase().contains("tableassignmentcontroller")
                    || path.toLowerCase().contains("table_assignmentcontroller"));
        }
        try (Stream<Path> paths = Files.walk(Path.of("."))) {
            assertThat(paths.filter(Files::isRegularFile).map(Path::toString).toList())
                .noneMatch(path -> path.endsWith(".vue")
                    && path.toLowerCase().contains("cleaning")
                    && !path.replace('\\', '/').endsWith("src/pages/CleaningCompletePage.vue"))
                .noneMatch(path -> path.toLowerCase().contains("openapi") && (path.endsWith(".yml") || path.endsWith(".yaml") || path.endsWith(".json")));
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

    private void assertStartTableDatabaseState(String idempotencyKey, UUID expectedTableId) {
        assertThat(fixture.countWhere("""
            select count(*) from cleanings
            where seating_id = ? and resource_type = 'dining_table'
              and table_id = ? and table_group_id is null and status = 'cleaning'
            """, TABLE_SEATING_ID, expectedTableId)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", expectedTableId))
            .isEqualTo("cleaning");
        assertStartCommonDatabaseState(idempotencyKey);
    }

    private void assertStartCommonDatabaseState(String idempotencyKey) {
        assertThat(fixture.scalarString("select status from seatings where id in (?, ?)", TABLE_SEATING_ID, GROUP_SEATING_ID))
            .isEqualTo("cleaning_triggered");
        assertThat(fixture.countWhere("select count(*) from seating_resources where status = 'released'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'cleaning.started'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'table.cleaning'")).isEqualTo(1);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(2);
        assertThat(fixture.count("audit_logs")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertBoundaryTablesRemainEmpty();
    }

    private void assertCompleteTableDatabaseState(String idempotencyKey, UUID cleaningId, UUID expectedTableId) {
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", expectedTableId))
            .isEqualTo("available");
        assertCompleteCommonDatabaseState(idempotencyKey, cleaningId);
    }

    private void assertCompleteCommonDatabaseState(String idempotencyKey, UUID cleaningId) {
        assertThat(fixture.scalarString("select status from cleanings where id = ?", cleaningId))
            .isEqualTo("released");
        assertThat(fixture.scalarInteger("select count(*) from cleanings where id = ? and completed_at is not null and released_at is not null", cleaningId))
            .isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'cleaning.completed'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'table.available'")).isEqualTo(1);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(2);
        assertThat(fixture.count("audit_logs")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertBoundaryTablesRemainEmpty();
    }

    private void assertClosedLoopDatabaseState(UUID cleaningId) {
        assertThat(fixture.scalarString("select status from cleanings where id = ?", cleaningId))
            .isEqualTo("released");
        assertThat(fixture.scalarInteger("select count(*) from cleanings where id = ? and completed_at is not null and released_at is not null", cleaningId))
            .isEqualTo(1);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", TABLE_ID))
            .isEqualTo("available");
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'cleaning.started'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'table.cleaning'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'cleaning.completed'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'table.available'")).isEqualTo(1);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(4);
        assertThat(fixture.count("audit_logs")).isEqualTo(2);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "closed-loop-start-table"))
            .isEqualTo("completed");
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "closed-loop-complete-table"))
            .isEqualTo("completed");
        assertBoundaryTablesRemainEmpty();
    }

    private void assertApplicationFailure(String idempotencyKey) {
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("failed");
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    private void assertNoCleaningMutationBeforeApplicationService() {
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertBoundaryTablesRemainEmpty();
    }

    private void assertBoundaryTablesRemainEmpty() {
        assertThat(fixture.count("reservations")).isEqualTo(0);
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
    }

    private StartCleaningRequest startRequest() {
        return new StartCleaningRequest(null, null);
    }

    private CompleteCleaningRequest completeRequest() {
        return new CompleteCleaningRequest(null, null);
    }

    private String startHash(UUID seatingId, StartCleaningRequest request) {
        return CleaningApplicationService.requestHash(new StartCleaningCommand(
            TENANT_ID,
            STORE_ID,
            seatingId,
            "ignored-by-hash",
            ACTOR_ID,
            "staff",
            request.reasonCode(),
            request.note()
        ));
    }

    private String completeHash(UUID cleaningId, CompleteCleaningRequest request) {
        return CleaningApplicationService.requestHash(new CompleteCleaningCommand(
            TENANT_ID,
            STORE_ID,
            cleaningId,
            "ignored-by-hash",
            ACTOR_ID,
            "staff",
            request.reasonCode(),
            request.note()
        ));
    }

    private String json(Object request) throws Exception {
        return objectMapper.writeValueAsString(request);
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
