package com.rpb.reservation.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ReservationStatusActionApiIntegrationTest {
    private static final String NO_SHOW_ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/no-show";
    private static final String COMPLETE_ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/complete";
    private static final String TODAY_ENDPOINT = "/api/v1/stores/{storeId}/reservations/today";
    private static final String CLEANING_START_ENDPOINT = "/api/v1/stores/{storeId}/seatings/{seatingId}/cleaning/start";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000978");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000978");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000978");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000978");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000978");
    private static final UUID SEATING_ID = UUID.fromString("60000000-0000-0000-0000-000000000978");
    private static final UUID SEATING_RESOURCE_ID = UUID.fromString("62000000-0000-0000-0000-000000000978");
    private static final UUID AREA_ID = UUID.fromString("70000000-0000-0000-0000-000000000978");
    private static final UUID TABLE_ID = UUID.fromString("71000000-0000-0000-0000-000000000978");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    private static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    private static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");
    private static final Instant NO_SHOW_AT = Instant.parse("2030-06-20T03:20:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2030-06-20T04:35:00Z");

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
        actorProvider.set(actor(Set.of("store_staff"), Set.of(
            "reservation.no_show",
            "reservation.complete",
            "reservation.today_view",
            "cleaning.start"
        ), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void marksConfirmedReservationNoShowAndTodayFilterCanReadIt() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-NOSHOW-0978", "confirmed");

        mockMvc.perform(post(NO_SHOW_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "no-show-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(noShowRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-NOSHOW-0978"))
            .andExpect(jsonPath("$.status").value("no_show"))
            .andExpect(jsonPath("$.noShowAt").value("2030-06-20T03:20:00Z"))
            .andExpect(jsonPath("$.noShowReasonCode").value("guest_no_show"))
            .andExpect(jsonPath("$.alreadyNoShow").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.no_show"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("no_show");
        assertThat(fixture.scalarString("select no_show_reason_code from reservations where id = ?", RESERVATION_ID))
            .isEqualTo("guest_no_show");
        assertThat(fixture.countWhere("select count(*) from business_events where event_type = 'reservation.no_show'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from state_transition_logs where transition_code = 'reservation.no_show'")).isEqualTo(1);
        assertThat(fixture.countWhere("select count(*) from audit_logs where operation_code = 'reservation.no_show'")).isEqualTo(1);

        mockMvc.perform(get(TODAY_ENDPOINT, STORE_ID)
                .param("businessDate", BUSINESS_DATE.toString())
                .param("status", "no_show"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.items[0].status").value("no_show"));
    }

    @Test
    void completesSeatedReservationAndAllowsCleaningToStartAfterwards() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-COMPLETE-0978", "seated");
        fixture.occupiedTableSeating();

        mockMvc.perform(post(COMPLETE_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "complete-success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeRequestJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.reservationCode").value("R-COMPLETE-0978"))
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.completedAt").value("2030-06-20T04:35:00Z"))
            .andExpect(jsonPath("$.seatingId").value(SEATING_ID.toString()))
            .andExpect(jsonPath("$.seatingStatus").value("completed"))
            .andExpect(jsonPath("$.alreadyCompleted").value(false))
            .andExpect(jsonPath("$.events[0]").value("reservation.completed"))
            .andExpect(jsonPath("$.events[1]").value("seating.completed"))
            .andExpect(jsonPath("$.idempotency.status").value("completed"));

        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("completed");
        assertThat(fixture.scalarString("select status from seatings where id = ?", SEATING_ID)).isEqualTo("completed");
        assertThat(fixture.scalarString("select status from dining_tables where id = ?", TABLE_ID)).isEqualTo("occupied");

        mockMvc.perform(post(CLEANING_START_ENDPOINT, STORE_ID, SEATING_ID)
                .header("Idempotency-Key", "cleaning-after-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reasonCode": "guest_finished",
                      "note": "Start cleaning after reservation completed"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cleaningStatus").value("cleaning"))
            .andExpect(jsonPath("$.tableStatus").value("cleaning"));

        assertThat(fixture.scalarString("select status from dining_tables where id = ?", TABLE_ID)).isEqualTo("cleaning");
        assertThat(fixture.scalarString("select status from seatings where id = ?", SEATING_ID)).isEqualTo("cleaning_triggered");
    }

    @Test
    void appGateDeniesNoShowWithoutPermissionAndDoesNotMutateReservation() throws Exception {
        fixture.reservation(RESERVATION_ID, "R-NOSHOW-DENIED", "confirmed");
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.today_view"), Set.of(STORE_ID)));

        mockMvc.perform(post(NO_SHOW_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "no-show-denied")
                .contentType(MediaType.APPLICATION_JSON)
                .content(noShowRequestJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        assertThat(fixture.scalarString("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
    }

    private static String noShowRequestJson() {
        return """
            {
              "noShowAt": "2030-06-20T03:20:00Z",
              "reasonCode": "guest_no_show",
              "note": "Past hold time"
            }
            """;
    }

    private static String completeRequestJson() {
        return """
            {
              "completedAt": "2030-06-20T04:35:00Z",
              "reasonCode": "guest_finished",
              "note": "Guest left table"
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
                values (?, 'tenant-status-action-it', 'Status Action Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-status-action-it', 'Status Action Store', 'active',
                    'Asia/Singapore', 'en-SG', 'DD-MM-YYYY', 'HH:mm', 'SGD')
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
                values (?, ?, 'C-STATUS-ACTION', 'regular', 'Status Guest', null, null, 'active')
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

        void occupiedTableSeating() {
            jdbc.update(
                """
                insert into store_areas (
                    id, tenant_id, store_id, area_code, display_name, status, sort_order
                )
                values (?, ?, ?, 'A', 'A区', 'active', 1)
                """,
                AREA_ID,
                TENANT_ID,
                STORE_ID
            );
            jdbc.update(
                """
                insert into dining_tables (
                    id, tenant_id, store_id, area_id, table_code, display_name,
                    capacity_min, capacity_max, status, is_combinable, created_at, updated_at
                )
                values (?, ?, ?, ?, 'A01', 'A01', 2, 4, 'occupied', true, ?, ?)
                """,
                TABLE_ID,
                TENANT_ID,
                STORE_ID,
                AREA_ID,
                utc(START_AT.minusSeconds(7200)),
                utc(START_AT.minusSeconds(3600))
            );
            jdbc.update(
                """
                insert into seatings (
                    id, tenant_id, store_id, reservation_id, seating_code, party_size_snapshot,
                    status, seated_at, created_at, updated_at
                )
                values (?, ?, ?, ?, 'S-STATUS-ACTION', 4, 'occupied', ?, ?, ?)
                """,
                SEATING_ID,
                TENANT_ID,
                STORE_ID,
                RESERVATION_ID,
                utc(START_AT),
                utc(START_AT.minusSeconds(300)),
                utc(START_AT.minusSeconds(300))
            );
            jdbc.update(
                """
                insert into seating_resources (
                    id, tenant_id, store_id, seating_id, resource_type, table_id,
                    assigned_at, status, created_at, updated_at
                )
                values (?, ?, ?, ?, 'dining_table', ?, ?, 'active', ?, ?)
                """,
                SEATING_RESOURCE_ID,
                TENANT_ID,
                STORE_ID,
                SEATING_ID,
                TABLE_ID,
                utc(START_AT),
                utc(START_AT),
                utc(START_AT)
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
