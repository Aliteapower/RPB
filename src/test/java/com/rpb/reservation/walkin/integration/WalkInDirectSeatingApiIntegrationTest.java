package com.rpb.reservation.walkin.integration;

import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.ACTOR_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.INVALID_TABLE_GROUP_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.INACTIVE_TABLE_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.LOCKED_TABLE_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.OTHER_STORE_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.RECOMMENDED_TABLE_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.SECOND_TABLE_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.SMALL_TABLE_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.STORE_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.TABLE_GROUP_ID;
import static com.rpb.reservation.walkin.integration.WalkInDirectSeatingIntegrationFixture.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.SeatWalkInDirectlyRequest;
import com.rpb.reservation.walkin.application.command.SeatWalkInDirectlyCommand;
import com.rpb.reservation.walkin.application.service.WalkInDirectSeatingApplicationService;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class WalkInDirectSeatingApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/walk-ins/direct-seating";
    private static final UUID LOOKUP_CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000009101");
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private WalkInDirectSeatingIntegrationFixture fixture;

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
        fixture = new WalkInDirectSeatingIntegrationFixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(staffActor(Set.of(STORE_ID), Set.of("store_staff")));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void seatsNoPhoneWalkInThroughFullApiToPostgresPath() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, null, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-no-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(SMALL_TABLE_ID.toString()))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulTableSeating("idem-no-phone", SMALL_TABLE_ID);
        assertThat(fixture.scalarInteger("select count(*) from customers where tenant_id = ? and phone_e164 is null", TENANT_ID))
            .isEqualTo(1);
    }

    @Test
    void seatsWalkInAndRefreshesRecognizedCustomerProfile() throws Exception {
        insertRecognizedCustomer();
        SeatWalkInDirectlyRequest request = new SeatWalkInDirectlyRequest(
            2,
            LOOKUP_CUSTOMER_ID,
            "陈女士",
            "女士",
            "+6598765430",
            SMALL_TABLE_ID,
            null,
            null,
            null
        );

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-refresh-walkin-customer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        assertThat(fixture.scalarString("select display_name from customers where id = ?", LOOKUP_CUSTOMER_ID))
            .isEqualTo("陈女士");
        assertThat(fixture.scalarString("select nickname from customers where id = ?", LOOKUP_CUSTOMER_ID))
            .isEqualTo("女士");
        assertThat(fixture.scalarString("select phone_e164 from customers where id = ?", LOOKUP_CUSTOMER_ID))
            .isEqualTo("+6598765430");
    }

    @Test
    void seatsWalkInWithSpecifiedDiningTable() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, SMALL_TABLE_ID, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-specified-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.type").value("TABLE"))
            .andExpect(jsonPath("$.resource.id").value(SMALL_TABLE_ID.toString()));

        assertSuccessfulTableSeating("idem-specified-table", SMALL_TABLE_ID);
    }

    @Test
    void seatsWalkInWithExistingTableGroup() throws Exception {
        SeatWalkInDirectlyRequest request = request(4, null, TABLE_GROUP_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-table-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.type").value("TABLE_GROUP"))
            .andExpect(jsonPath("$.resource.id").value(TABLE_GROUP_ID.toString()));

        assertThat(fixture.count("walk_ins")).isEqualTo(1);
        assertThat(fixture.count("seatings")).isEqualTo(1);
        assertThat(fixture.count("seating_resources")).isEqualTo(1);
        assertThat(fixture.scalarInteger("""
            select count(*) from seating_resources
            where resource_type = 'table_group'
              and table_group_id = ?
              and table_id is null
              and status = 'active'
            """, TABLE_GROUP_ID)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "idem-table-group"))
            .isEqualTo("completed");
        assertBoundaryTablesRemainEmpty();
    }

    @Test
    void autoSelectedTableUsesFirstAvailableCandidate() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, null, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-auto")
                .contentType(MediaType.APPLICATION_JSON)
            .content(json(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resource.id").value(SMALL_TABLE_ID.toString()));

        assertThat(fixture.scalarString("select status from dining_tables where id = ?", SMALL_TABLE_ID))
            .isEqualTo("occupied");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", RECOMMENDED_TABLE_ID))
            .isEqualTo("available");
    }

    @Test
    void completedIdempotencyReplayReturnsPreviousResponseWithoutMutation() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, null, null);
        UUID replayWalkInId = UUID.fromString("60000000-0000-0000-0000-000000000101");
        UUID replaySeatingId = UUID.fromString("70000000-0000-0000-0000-000000000101");
        fixture.idempotencyRecord("idem-replay", requestHash(request), "completed", replayWalkInId, replaySeatingId, RECOMMENDED_TABLE_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.walkInId").value(replayWalkInId.toString()))
            .andExpect(jsonPath("$.seatingId").value(replaySeatingId.toString()))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertNoWalkInSeatingMutation();
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyDoesNotWriteBusinessRows() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(2, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MISSING_IDEMPOTENCY_KEY"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void invalidPartySizeDoesNotWriteBusinessRows() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-party")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(0, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PARTY_SIZE"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void invalidPhoneDoesNotWriteBusinessRows() throws Exception {
        SeatWalkInDirectlyRequest request = new SeatWalkInDirectlyRequest(2, null, "Guest", null, "91234567", null, null, null, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_PHONE_E164"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void tableIdAndTableGroupIdTogetherDoesNotWriteBusinessRows() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, RECOMMENDED_TABLE_ID, TABLE_GROUP_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-resource-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("RESOURCE_CONFLICT"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void inactiveTableFailsWithoutCreatingWalkInOrSeating() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, INACTIVE_TABLE_ID, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-inactive-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_NOT_AVAILABLE"));

        assertApplicationFailureWithoutSeating("idem-inactive-table");
    }

    @Test
    void activeTableLockConflictFailsWithoutCreatingWalkInOrSeating() throws Exception {
        fixture.createActiveLock(SMALL_TABLE_ID, "lock-recommended");

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-lock-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(2, SMALL_TABLE_ID, null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_LOCK_CONFLICT"));

        assertApplicationFailureWithoutSeating("idem-lock-conflict");
        assertThat(fixture.count("table_locks")).isEqualTo(1);
    }

    @Test
    void capacityInsufficientFailsWithoutCreatingWalkInOrSeating() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-capacity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(6, SMALL_TABLE_ID, null))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_CAPACITY_INSUFFICIENT"));

        assertApplicationFailureWithoutSeating("idem-capacity");
    }

    @Test
    void invalidTableGroupFailsWithoutCreatingWalkInOrSeating() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-invalid-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(4, null, INVALID_TABLE_GROUP_ID))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TABLE_GROUP_INVALID"));

        assertApplicationFailureWithoutSeating("idem-invalid-group");
    }

    @Test
    void overrideMissingFailsWithoutCreatingWalkInOrSeating() throws Exception {
        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(2, SECOND_TABLE_ID, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("OVERRIDE_REASON_REQUIRED"));

        assertApplicationFailureWithoutSeating("idem-override");
    }

    @Test
    void idempotencyInProgressDoesNotCreateNewMutation() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, null, null);
        fixture.idempotencyRecord("idem-started", requestHash(request), "started", null, null, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-started")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_IN_PROGRESS"))
            .andExpect(jsonPath("$.idempotency.status").value("started"));

        assertNoWalkInSeatingMutation();
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void failedIdempotencyRequiresNewKeyWithoutNewMutation() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, null, null);
        fixture.idempotencyRecord("idem-failed", requestHash(request), "failed", null, null, null);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY"));

        assertNoWalkInSeatingMutation();
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void idempotencyHashConflictDoesNotCreateNewMutation() throws Exception {
        SeatWalkInDirectlyRequest request = request(2, null, null);
        fixture.idempotencyRecord("idem-hash-conflict", "different-request-hash", "completed", UUID.randomUUID(), UUID.randomUUID(), RECOMMENDED_TABLE_ID);

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-hash-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        assertNoWalkInSeatingMutation();
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
    }

    @Test
    void forbiddenRoleStopsBeforeApplicationService() throws Exception {
        actorProvider.set(staffActor(Set.of(STORE_ID), Set.of("customer")));

        mockMvc.perform(post(ENDPOINT, STORE_ID)
                .header("Idempotency-Key", "idem-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(2, null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertNoMutationBeforeApplicationService();
    }

    @Test
    void storeScopeMismatchStopsBeforeApplicationService() throws Exception {
        actorProvider.set(staffActor(Set.of(STORE_ID), Set.of("store_staff")));

        mockMvc.perform(post(ENDPOINT, OTHER_STORE_ID)
                .header("Idempotency-Key", "idem-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request(2, null, null))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));

        assertNoMutationBeforeApplicationService();
    }

    private void assertSuccessfulTableSeating(String idempotencyKey, UUID expectedTableId) {
        assertThat(fixture.count("walk_ins")).isEqualTo(1);
        assertThat(fixture.count("seatings")).isEqualTo(1);
        assertThat(fixture.count("seating_resources")).isEqualTo(1);
        assertThat(fixture.scalarInteger("""
            select count(*) from seatings
            where walk_in_id is not null
              and reservation_id is null
              and queue_ticket_id is null
              and status = 'occupied'
            """)).isEqualTo(1);
        assertThat(fixture.scalarInteger("""
            select count(*) from seating_resources
            where resource_type = 'dining_table'
              and table_id = ?
              and table_group_id is null
              and status = 'active'
            """, expectedTableId)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", expectedTableId))
            .isEqualTo("occupied");
        assertThat(fixture.count("business_events")).isEqualTo(4);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(4);
        assertThat(fixture.count("audit_logs")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertBoundaryTablesRemainEmpty();
    }

    private void assertApplicationFailureWithoutSeating(String idempotencyKey) {
        assertNoWalkInSeatingMutation();
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("failed");
        assertThat(fixture.count("audit_logs")).isEqualTo(1);
        assertBoundaryTablesRemainEmpty();
    }

    private void assertNoMutationBeforeApplicationService() {
        assertNoWalkInSeatingMutation();
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertBoundaryTablesRemainEmpty();
    }

    private void assertNoWalkInSeatingMutation() {
        assertThat(fixture.count("walk_ins")).isEqualTo(0);
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("seating_resources")).isEqualTo(0);
    }

    private void assertBoundaryTablesRemainEmpty() {
        assertThat(fixture.count("reservations")).isEqualTo(0);
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("cleanings")).isEqualTo(0);
        assertThat(fixture.count("turnovers")).isEqualTo(0);
    }

    private SeatWalkInDirectlyRequest request(int partySize, UUID tableId, UUID tableGroupId) {
        return new SeatWalkInDirectlyRequest(partySize, null, "Guest", null, null, tableId, tableGroupId, null, null);
    }

    private void insertRecognizedCustomer() {
        jdbc.update(
            """
            insert into customers (
                id, tenant_id, customer_code, customer_type, display_name,
                nickname, phone_e164, status
            )
            values (?, ?, 'C-WALKIN-LOOKUP', 'regular', '陈先生', '先生', '+6598765430', 'active')
            """,
            LOOKUP_CUSTOMER_ID,
            TENANT_ID
        );
    }

    private String requestHash(SeatWalkInDirectlyRequest request) {
        return WalkInDirectSeatingApplicationService.requestHash(new SeatWalkInDirectlyCommand(
            TENANT_ID,
            STORE_ID,
            request.partySize(),
            request.customerId(),
            request.customerName(),
            request.customerNickname(),
            request.phoneE164(),
            request.tableId(),
            request.tableGroupId(),
            "ignored-by-hash",
            ACTOR_ID,
            "staff",
            request.overrideReasonCode(),
            request.overrideNote()
        ));
    }

    private String json(SeatWalkInDirectlyRequest request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private static CurrentActor staffActor(Set<UUID> storeIds, Set<String> roles) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            roles.contains("customer") ? "customer" : "staff",
            roles,
            Set.of("walkin.direct_seating.create"),
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
