package com.rpb.reservation.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ReservationCancelApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/cancel";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000981");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000981");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000981");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000981");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000981");
    private static final UUID QUEUE_GROUP_ID = UUID.fromString("92000000-0000-0000-0000-000000000981");
    private static final UUID QUEUE_TICKET_ID = UUID.fromString("91000000-0000-0000-0000-000000000981");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2030-06-20T03:20:00Z");

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
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.cancel"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void cancelsConfirmedReservationThroughApiAndWritesEvidenceToPostgres() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CANCEL-0981", "confirmed");

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "cancel-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-CANCEL-0981"))
            .andExpect(jsonPath("$.status").value("cancelled"))
            .andExpect(jsonPath("$.cancelledAt").value("2030-06-20T03:20:00Z"))
            .andExpect(jsonPath("$.cancellationReasonCode").value("guest_requested"))
            .andExpect(jsonPath("$.alreadyCancelled").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.cancelled"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertSuccessfulCancellationDatabaseState("cancel-success", "confirmed");
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void completedIdempotencyReplayDoesNotDuplicateCancellationEvidence() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CANCEL-REPLAY", "confirmed");

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "cancel-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "cancel-replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true))
            .andExpect(jsonPath("$.events").isEmpty());

        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.cancelled'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'reservation.cancel'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.cancel'")).isEqualTo(1);
        assertThat(fixture.count("idempotency_records")).isEqualTo(1);
        assertBoundaryTablesRemainUnchanged();
    }

    @Test
    void arrivedReservationFailsWithoutCancellingQueueOrSeatingSideEffects() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CANCEL-ARRIVED", "arrived");
        fixture.queueGroup();
        fixture.queueTicket();

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "cancel-arrived")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("RESERVATION_CANNOT_CANCEL_ARRIVED"))
            .andExpect(jsonPath("$.idempotency.status").value("failed"));

        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("arrived");
        assertThat(fixture.scalarString("select cancellation_reason_code from reservations where id = ?", RESERVATION_ID)).isNull();
        assertThat(fixture.scalarString("select status from queue_tickets where id = ?", QUEUE_TICKET_ID)).isEqualTo("waiting");
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.cancelled'")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'reservation.cancel'")).isEqualTo(0);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.cancel.failed'")).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", "cancel-arrived"))
            .isEqualTo("failed");
        assertBoundaryTablesRemainUnchangedExceptQueueTicket();
    }

    @Test
    void appGateDenyCasesAuditAndDoNotMutateBusinessState() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-CANCEL-GATE", "confirmed");
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.check_in"), Set.of(STORE_ID)));

        mockMvc.perform(post(ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "cancel-gate-permission")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
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
              and after_json ->> 'denyReason' = 'PERMISSION_DENIED'
              and after_json ->> 'requiredPermission' = 'reservation.cancel'
            """, TENANT_ID, STORE_ID, ACTOR_ID)).isEqualTo(1);
        assertBoundaryTablesRemainUnchanged();
    }

    private void assertSuccessfulCancellationDatabaseState(String idempotencyKey, String fromStatus) {
        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("cancelled");
        assertThat(fixture.scalarString("select cancellation_reason_code from reservations where id = ?", RESERVATION_ID))
            .isEqualTo("guest_requested");
        assertThat(fixture.scalarOffsetDateTime("select updated_at from reservations where id = ?", RESERVATION_ID).toInstant())
            .isEqualTo(CANCELLED_AT);
        assertThat(fixture.countWhere("""
            select count(*) from business_events
            where event_type = 'reservation.cancelled'
              and target_type = 'reservation'
              and target_id = ?
              and metadata ->> 'cancelledAt' = ?
              and metadata ->> 'reasonCode' = 'guest_requested'
              and metadata ->> 'note' = 'Customer called to cancel'
            """, RESERVATION_ID, CANCELLED_AT.toString())).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from state_transition_logs
            where target_type = 'reservation'
              and target_id = ?
              and from_status = ?
              and to_status = 'cancelled'
              and transition_code = 'reservation.cancel'
            """, RESERVATION_ID, fromStatus)).isEqualTo(1);
        assertThat(fixture.countWhere("""
            select count(*) from audit_logs
            where operation_code = 'reservation.cancel'
              and target_type = 'reservation'
              and target_id = ?
              and metadata ->> 'reasonCode' = 'guest_requested'
            """, RESERVATION_ID)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("completed");
        assertThat(fixture.scalarString("select action from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("cancel_reservation");
        assertThat(fixture.scalarString("select target_type from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo("reservation");
        assertThat(fixture.scalarString("select target_id::text from idempotency_records where idempotency_key = ?", idempotencyKey))
            .isEqualTo(RESERVATION_ID.toString());
    }

    private void assertBoundaryTablesRemainUnchanged() {
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertBoundaryTablesRemainUnchangedExceptQueueTicket();
    }

    private void assertBoundaryTablesRemainUnchangedExceptQueueTicket() {
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("table_locks")).isEqualTo(0);
        assertThat(fixture.count("reservation_preassignments")).isEqualTo(0);
    }

    private static String requestJson() {
        return """
            {
              "cancelledAt": "2030-06-20T03:20:00Z",
              "reasonCode": "guest_requested",
              "note": "Customer called to cancel"
            }
            """;
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
                values (?, 'tenant-cancel-api-it', 'Cancel API Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-cancel-api-it', 'Cancel API Store', 'active',
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
                values (?, ?, 'C-CANCEL-API', 'regular', 'Cancel Guest', null, null, 'active')
                """,
                CUSTOMER_ID,
                TENANT_ID
            );
            enableReservationQueueApp();
        }

        void reservation(UUID reservationId, String reservationCode, String status) {
            jdbc.update(
                """
                insert into reservations (
                    id, tenant_id, store_id, customer_id, reservation_code, party_size,
                    business_date, reserved_start_at, reserved_end_at, hold_until_at,
                    status, source_channel, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, 4, ?, ?, ?, ?, ?, 'staff', 'Window seat',
                    ?, ?)
                """,
                reservationId,
                TENANT_ID,
                STORE_ID,
                CUSTOMER_ID,
                reservationCode,
                BUSINESS_DATE,
                utc(START_AT),
                utc(END_AT),
                utc(HOLD_UNTIL_AT),
                status,
                utc(START_AT.minusSeconds(7200)),
                utc(START_AT.minusSeconds(3600))
            );
        }

        void queueGroup() {
            jdbc.update(
                """
                insert into queue_groups (
                    id, tenant_id, store_id, group_code, min_party_size, max_party_size,
                    display_i18n_key, status, sort_order
                )
                values (?, ?, ?, '3-4', 3, 4, 'queue.group.3-4', 'active', 1)
                """,
                QUEUE_GROUP_ID,
                TENANT_ID,
                STORE_ID
            );
        }

        void queueTicket() {
            jdbc.update(
                """
                insert into queue_tickets (
                    id, tenant_id, store_id, queue_group_id, customer_id, reservation_id,
                    walk_in_id, ticket_number, party_size, business_date, status,
                    queue_position, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, null, 1, 4, ?, 'waiting',
                    1, 'Existing active reservation queue ticket', now(), now())
                """,
                QUEUE_TICKET_ID,
                TENANT_ID,
                STORE_ID,
                QUEUE_GROUP_ID,
                CUSTOMER_ID,
                RESERVATION_ID,
                BUSINESS_DATE
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

        OffsetDateTime scalarOffsetDateTime(String sql, Object... args) {
            return jdbc.queryForObject(sql, OffsetDateTime.class, args);
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
