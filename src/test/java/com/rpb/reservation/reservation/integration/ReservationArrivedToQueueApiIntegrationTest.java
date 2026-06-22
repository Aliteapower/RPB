package com.rpb.reservation.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.reservation.application.command.QueueArrivedReservationCommand;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class ReservationArrivedToQueueApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/queue";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000971");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000971");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000971");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000971");
    private static final UUID OTHER_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000972");
    private static final UUID SMALL_QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000971");
    private static final UUID MEDIUM_QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000972");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000971");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    private Fixture fixture;

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
        fixture = new Fixture(jdbc);
        fixture.reset();
        fixture.createBaseStore();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.queue"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void queuesArrivedReservationThroughApiAndWritesEvidenceToPostgres() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QUEUE-0971", "arrived", 4);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-QUEUE-0971"))
            .andExpect(jsonPath("$.reservationStatus").value("arrived"))
            .andExpect(jsonPath("$.queueTicketNumber").value(1))
            .andExpect(jsonPath("$.queueTicketStatus").value("waiting"))
            .andExpect(jsonPath("$.queueGroupId").value(MEDIUM_QUEUE_GROUP_ID.toString()))
            .andExpect(jsonPath("$.partySize").value(4))
            .andExpect(jsonPath("$.partySizeGroup").value("3-4"))
            .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
            .andExpect(jsonPath("$.queuePosition").value(1))
            .andExpect(jsonPath("$.alreadyQueued").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.queued"))
            .andExpect(jsonPath("$.events[1]").value("queue_ticket.created"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulQueueDatabaseState("queue-success", RESERVATION_ID);
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void completedIdempotencyReplayDoesNotDuplicateQueueTicketOrEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QUEUE-REPLAY", "arrived", 4);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
            .andExpect(jsonPath("$.events").isEmpty());

        assertThat(fixture.count("queue_tickets")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type in ('reservation.queued', 'queue_ticket.created')")).isEqualTo(2);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.create'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.queue'")).isEqualTo(1);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void alreadyQueuedWithNewKeyReturnsExistingTicketWithoutDuplicateBusinessEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QUEUE-ALREADY", "arrived", 4);
        fixture.existingQueueTicket(RESERVATION_ID, QUEUE_TICKET_ID, MEDIUM_QUEUE_GROUP_ID, 3, 2);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-already")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueTicketId").value(QUEUE_TICKET_ID.toString()))
            .andExpect(jsonPath("$.queueTicketNumber").value(3))
            .andExpect(jsonPath("$.queuePosition").value(2))
            .andExpect(jsonPath("$.alreadyQueued").value(true))
            .andExpect(jsonPath("$.events").isEmpty())
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        assertThat(fixture.count("queue_tickets")).isEqualTo(1);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "queue-already"))
            .isEqualTo("completed");
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void idempotencyStatesReturnStableErrorsWithoutNewQueueTicket() throws Exception {
        assertIdempotencyFailure("queue-in-progress", "started", "IDEMPOTENCY_IN_PROGRESS", "started", hash(RESERVATION_ID, "3-4"));
        assertIdempotencyFailure("queue-failed", "failed", "IDEMPOTENCY_FAILED_REQUIRES_NEW_KEY", "failed", hash(RESERVATION_ID, "3-4"));
        assertIdempotencyFailure("queue-hash-conflict", "completed", "IDEMPOTENCY_CONFLICT", "conflict", "different-request-hash");
    }

    @Test
    void applicationFailuresReturnStableApiErrorsWithoutQueueTicket() throws Exception {
        assertApplicationFailure("confirmed", 4, "3-4", 409, "RESERVATION_STATUS_NOT_ARRIVED");
        assertApplicationFailure("cancelled", 4, "3-4", 409, "RESERVATION_CANNOT_QUEUE_CANCELLED");
        assertApplicationFailure("arrived", 4, "1-2", 409, "QUEUE_GROUP_PARTY_SIZE_MISMATCH");

        fixture.reset();
        fixture.createBaseStore();
        fixture.deleteQueueGroup("3-4");
        fixture.reservation(RESERVATION_ID, "R-QUEUE-NOGROUP", "arrived", 4);
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-no-group")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("QUEUE_GROUP_NOT_FOUND"));
        assertNoQueueMutationAfterFailure("queue-no-group");

        fixture.reset();
        fixture.createBaseStore();
        mockMvc.perform(post(ENDPOINT, STORE_ID, OTHER_RESERVATION_ID)
                .header("Idempotency-Key", "queue-not-found")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("RESERVATION_NOT_FOUND"));
        assertNoQueueMutationAfterFailure("queue-not-found");
    }

    @Test
    void appGateDenyCasesAuditAndDoNotMutateBusinessState() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-QUEUE-GATE", "arrived", 4);

        jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID);
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-gate-tenant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("TENANT_APP_NOT_ENABLED"));
        assertAppGateDeniedWithoutBusinessMutation("TENANT_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-QUEUE-GATE", "arrived", 4);
        jdbc.update("""
            update store_app_settings
            set is_enabled = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-gate-store")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_APP_NOT_ENABLED"));
        assertAppGateDeniedWithoutBusinessMutation("STORE_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-QUEUE-GATE", "arrived", 4);
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.seat"), Set.of(STORE_ID)));
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "queue-gate-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
        assertAppGateDeniedWithoutBusinessMutation("PERMISSION_DENIED");
    }

    private void assertSuccessfulQueueDatabaseState(String idempotencyKey, UUID reservationId) {
        assertThat(fixture.count("queue_tickets")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from reservations where id = ?", reservationId)).isEqualTo("arrived");
        assertThat(fixture.scalarString("select reservation_id::text from queue_tickets")).isEqualTo(reservationId.toString());
        assertThat(fixture.scalarString("select walk_in_id::text from queue_tickets")).isNull();
        assertThat(fixture.scalarString("select status from queue_tickets")).isEqualTo("waiting");
        assertThat(fixture.scalarInteger("select ticket_number from queue_tickets")).isEqualTo(1);
        assertThat(fixture.scalarInteger("select queue_position from queue_tickets")).isEqualTo(1);
        assertThat(fixture.scalarString("select queue_group_id::text from queue_tickets")).isEqualTo(MEDIUM_QUEUE_GROUP_ID.toString());
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.queued'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'queue_ticket.created'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'queue_ticket.create'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.queue'")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertThat(fixture.scalarString("select target_type from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("queue_ticket");
    }

    private void assertIdempotencyFailure(
        String key,
        String idempotencyStatus,
        String expectedCode,
        String expectedResponseStatus,
        String requestHash
    ) throws Exception {
        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-QUEUE-IDEM", "arrived", 4);
        fixture.idempotencyRecord(key, requestHash, idempotencyStatus);

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson("3-4")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.idempotency.status").value(expectedResponseStatus));

        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
    }

    private void assertApplicationFailure(
        String reservationStatus,
        int partySize,
        String partySizeGroup,
        int expectedHttpStatus,
        String expectedCode
    ) throws Exception {
        fixture.reset();
        fixture.createBaseStore();
        fixture.reservation(RESERVATION_ID, "R-QUEUE-FAIL-" + reservationStatus, reservationStatus, partySize);

        String idempotencyKey = "queue-fail-" + expectedCode.toLowerCase();
        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(partySizeGroup)))
            .andExpect(status().is(expectedHttpStatus))
            .andExpect(jsonPath("$.error.code").value(expectedCode))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        assertNoQueueMutationAfterFailure(idempotencyKey);
    }

    private void assertNoQueueMutationAfterFailure(String idempotencyKey) {
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.queue.failed'")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("failed");
        assertBoundaryTablesRemainUnchanged();
    }

    private void assertAppGateDeniedWithoutBusinessMutation(String expectedReason) {
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and operator_user_id = ?
              and operator_role = 'staff'
              and after_json ->> 'denyReason' = ?
              and after_json ->> 'requiredPermission' = 'reservation.queue'
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason)).isEqualTo(1);
        assertBoundaryTablesRemainUnchanged();
    }

    private void assertBoundaryTablesRemainUnchanged() {
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("table_locks")).isEqualTo(0);
        assertThat(fixture.count("reservation_preassignments")).isEqualTo(0);
    }

    private static String requestJson(String partySizeGroup) {
        return """
            {
              "partySizeGroup": "%s",
              "reasonCode": "NO_TABLE_AVAILABLE",
              "note": "Customer is waiting near entrance"
            }
            """.formatted(partySizeGroup);
    }

    private static String hash(UUID reservationId, String partySizeGroup) {
        return ReservationArrivedToQueueApplicationService.requestHash(new QueueArrivedReservationCommand(
            TENANT_ID,
            STORE_ID,
            reservationId,
            "unused-for-hash",
            ACTOR_ID,
            "staff",
            partySizeGroup,
            "NO_TABLE_AVAILABLE",
            "Customer is waiting near entrance"
        ));
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
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

    private static final class Fixture {
        private final JdbcTemplate jdbc;

        private Fixture(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reset() {
            jdbc.execute("truncate table tenants cascade");
        }

        void createBaseStore() {
            jdbc.update(
                """
                insert into tenants (id, tenant_code, display_name, status, default_locale)
                values (?, 'tenant-queue-api-it', 'Queue API Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-queue-api-it', 'Queue API Store', 'active',
                    'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
                """,
                STORE_ID,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into store_policies (
                    id, tenant_id, store_id, reservation_hold_minutes, queue_call_hold_minutes,
                    expected_dining_minutes, queue_rejoin_policy_code, table_assignment_policy_code,
                    effective_from_at
                )
                values (gen_random_uuid(), ?, ?, 15, 3, 90, 'same_group_tail',
                    'default_capacity_time_area_group', now() - interval '1 day')
                """,
                TENANT_ID,
                STORE_ID
            );
            jdbc.update(
                """
                insert into customers (
                    id, tenant_id, customer_code, customer_type, display_name,
                    nickname, phone_e164, status
                )
                values (?, ?, 'C-QUEUE-API', 'regular', 'Queue Guest', null, null, 'active')
                """,
                CUSTOMER_ID,
                TENANT_ID
            );
            enableReservationQueueApp();
            queueGroup(SMALL_QUEUE_GROUP_ID, "1-2", 1, 2, 1);
            queueGroup(MEDIUM_QUEUE_GROUP_ID, "3-4", 3, 4, 2);
            queueGroup(UUID.fromString("92000000-0000-0000-0000-000000000973"), "5-6", 5, 6, 3);
            queueGroup(UUID.fromString("92000000-0000-0000-0000-000000000974"), "7+", 7, null, 4);
        }

        void reservation(UUID reservationId, String reservationCode, String status, int partySize) {
            jdbc.update(
                """
                insert into reservations (
                    id, tenant_id, store_id, customer_id, reservation_code, party_size,
                    business_date, reserved_start_at, reserved_end_at, hold_until_at,
                    status, source_channel, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'staff', 'Window seat',
                    ?, ?)
                """,
                reservationId,
                TENANT_ID,
                STORE_ID,
                CUSTOMER_ID,
                reservationCode,
                partySize,
                BUSINESS_DATE,
                utc(START_AT),
                utc(END_AT),
                utc(HOLD_UNTIL_AT),
                status,
                utc(START_AT.minusSeconds(7200)),
                utc(START_AT.minusSeconds(3600))
            );
        }

        void existingQueueTicket(UUID reservationId, UUID queueTicketId, UUID queueGroupId, int ticketNumber, int queuePosition) {
            jdbc.update(
                """
                insert into queue_tickets (
                    id, tenant_id, store_id, queue_group_id, customer_id, reservation_id,
                    walk_in_id, ticket_number, party_size, business_date, status,
                    queue_position, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, null, ?, 4, ?, 'waiting',
                    ?, 'Existing ticket', now(), now())
                """,
                queueTicketId,
                TENANT_ID,
                STORE_ID,
                queueGroupId,
                CUSTOMER_ID,
                reservationId,
                ticketNumber,
                BUSINESS_DATE,
                queuePosition
            );
        }

        void deleteQueueGroup(String groupCode) {
            jdbc.update(
                "delete from queue_groups where tenant_id = ? and store_id = ? and group_code = ?",
                TENANT_ID,
                STORE_ID,
                groupCode
            );
        }

        void idempotencyRecord(String key, String hash, String status) {
            jdbc.update(
                """
                insert into idempotency_records (
                    id, tenant_id, store_id, idempotency_key, source, action,
                    target_type, target_id, request_hash, response_snapshot, status, expires_at
                )
                values (gen_random_uuid(), ?, ?, ?, 'staff', 'queue_arrived_reservation',
                    null, null, ?, null, ?, now() + interval '30 minutes')
                """,
                TENANT_ID,
                STORE_ID,
                key,
                hash,
                status
            );
        }

        int count(String tableName) {
            return jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
        }

        int countWhere(String sql, Object... args) {
            return jdbc.queryForObject(sql, Integer.class, args);
        }

        String scalarString(String sql, Object... args) {
            return jdbc.queryForObject(sql, String.class, args);
        }

        Integer scalarInteger(String sql, Object... args) {
            return jdbc.queryForObject(sql, Integer.class, args);
        }

        private void queueGroup(UUID groupId, String groupCode, int minPartySize, Integer maxPartySize, int sortOrder) {
            jdbc.update(
                """
                insert into queue_groups (
                    id, tenant_id, store_id, group_code, min_party_size, max_party_size,
                    display_i18n_key, status, sort_order
                )
                values (?, ?, ?, ?, ?, ?, ?, 'active', ?)
                """,
                groupId,
                TENANT_ID,
                STORE_ID,
                groupCode,
                minPartySize,
                maxPartySize,
                "queue.group." + groupCode,
                sortOrder
            );
        }

        private void enableReservationQueueApp() {
            jdbc.update(
                """
                insert into tenant_app_entitlements (
                    tenant_id, app_key, status, valid_from, valid_until, config_json, enabled_at
                )
                values (?, 'reservation_queue', 'enabled', now(), null, '{}'::jsonb, now())
                on conflict (tenant_id, app_key) do nothing
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into store_app_settings (
                    tenant_id, store_id, app_key, is_enabled, entry_visible, config_json, enabled_at
                )
                values (?, ?, 'reservation_queue', true, true, '{}'::jsonb, now())
                on conflict (tenant_id, store_id, app_key) do update
                set is_enabled = true,
                    entry_visible = true,
                    disabled_at = null,
                    updated_at = now()
                """,
                TENANT_ID,
                STORE_ID
            );
        }

        private static OffsetDateTime utc(Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }
}
