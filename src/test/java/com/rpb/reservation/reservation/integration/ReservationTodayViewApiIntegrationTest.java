package com.rpb.reservation.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReservationTodayViewApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/today";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000991");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000991");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000991");
    private static final UUID CUSTOMER_PHONE_ID = UUID.fromString("40000000-0000-0000-0000-000000000991");
    private static final UUID CUSTOMER_NO_PHONE_ID = UUID.fromString("40000000-0000-0000-0000-000000000992");
    private static final UUID CONFIRMED_ID = UUID.fromString("50000000-0000-0000-0000-000000000991");
    private static final UUID ARRIVED_ID = UUID.fromString("50000000-0000-0000-0000-000000000992");
    private static final UUID SEATED_ID = UUID.fromString("50000000-0000-0000-0000-000000000993");
    private static final UUID CANCELLED_ID = UUID.fromString("50000000-0000-0000-0000-000000000994");
    private static final UUID NO_SHOW_ID = UUID.fromString("50000000-0000-0000-0000-000000000995");
    private static final UUID COMPLETED_ID = UUID.fromString("50000000-0000-0000-0000-000000000996");
    private static final UUID DRAFT_ID = UUID.fromString("50000000-0000-0000-0000-000000000997");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");

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
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.today_view"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void returnsOperationalTodayReservationsSortedWithMaskedPhoneAndNoWriteSideEffects() throws Exception {
        fixture.allStatusReservations(BUSINESS_DATE);

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", BUSINESS_DATE.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.storeId").value(STORE_ID.toString()))
            .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
            .andExpect(jsonPath("$.storeTimezone").value("Asia/Singapore"))
            .andExpect(jsonPath("$.statusFilter").value("operational"))
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].reservationId").value(ARRIVED_ID.toString()))
            .andExpect(jsonPath("$.items[0].reservationCode").value("R-TV-ARRIVED"))
            .andExpect(jsonPath("$.items[0].status").value("arrived"))
            .andExpect(jsonPath("$.items[0].phoneMasked").doesNotExist())
            .andExpect(jsonPath("$.items[1].reservationId").value(SEATED_ID.toString()))
            .andExpect(jsonPath("$.items[1].reservationCode").value("R-TV-SEATED"))
            .andExpect(jsonPath("$.items[1].phoneMasked").value("****4567"))
            .andExpect(jsonPath("$.items[1].phoneE164").doesNotExist())
            .andExpect(jsonPath("$.items[2].reservationId").value(CONFIRMED_ID.toString()))
            .andExpect(jsonPath("$.items[2].reservationCode").value("R-TV-CONFIRMED"))
            .andExpect(jsonPath("$.items[2].customerName").value("Phone Guest"))
            .andExpect(jsonPath("$.items[2].customerNickname").value("VIP"))
            .andExpect(jsonPath("$.items[2].note").value("Confirmed note"))
            .andExpect(jsonPath("$.idempotency").doesNotExist());

        assertReadOnlyBoundary();
        assertThat(fixture.scalarString("select status from reservations where id = ?", CONFIRMED_ID)).isEqualTo("confirmed");
        assertThat(fixture.scalarString("select status from reservations where id = ?", ARRIVED_ID)).isEqualTo("arrived");
        assertThat(fixture.scalarString("select status from reservations where id = ?", SEATED_ID)).isEqualTo("seated");
    }

    @Test
    void statusAllReturnsCompletedCancelledNoShowAndOperationalButNotDraft() throws Exception {
        fixture.allStatusReservations(BUSINESS_DATE);

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", BUSINESS_DATE.toString())
                .param("status", "all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusFilter").value("all"))
            .andExpect(jsonPath("$.items.length()").value(6))
            .andExpect(jsonPath("$.items[0].reservationCode").value("R-TV-ARRIVED"))
            .andExpect(jsonPath("$.items[1].reservationCode").value("R-TV-SEATED"))
            .andExpect(jsonPath("$.items[2].reservationCode").value("R-TV-CONFIRMED"))
            .andExpect(jsonPath("$.items[3].reservationCode").value("R-TV-CANCELLED"))
            .andExpect(jsonPath("$.items[4].reservationCode").value("R-TV-NOSHOW"))
            .andExpect(jsonPath("$.items[5].reservationCode").value("R-TV-COMPLETED"));

        assertReadOnlyBoundary();
    }

    @Test
    void singleStatusFilterReturnsOnlyRequestedSupportedStatus() throws Exception {
        fixture.allStatusReservations(BUSINESS_DATE);

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", BUSINESS_DATE.toString())
                .param("status", "cancelled"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusFilter").value("cancelled"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].reservationId").value(CANCELLED_ID.toString()))
            .andExpect(jsonPath("$.items[0].status").value("cancelled"));

        assertReadOnlyBoundary();
    }

    @Test
    void missingBusinessDateDefaultsToCurrentDateInStoreTimezone() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Singapore"));
        fixture.reservation(CONFIRMED_ID, CUSTOMER_PHONE_ID, "R-TV-TODAY", "confirmed", today, 0, 0, "Today note");

        mockMvc.perform(get(ENDPOINT, STORE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessDate").value(today.toString()))
            .andExpect(jsonPath("$.storeTimezone").value("Asia/Singapore"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].reservationCode").value("R-TV-TODAY"));

        assertReadOnlyBoundary();
    }

    @Test
    void invalidBusinessDateAndInvalidStatusReturnStableErrorEnvelopeWithoutMutation() throws Exception {
        fixture.allStatusReservations(BUSINESS_DATE);

        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", "2030/06/20"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_BUSINESS_DATE"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.today_view.invalid_business_date"));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", BUSINESS_DATE.toString())
                .param("status", "draft"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_FILTER"))
            .andExpect(jsonPath("$.error.messageKey").value("reservation.today_view.invalid_status_filter"));

        assertReadOnlyBoundary();
    }

    @Test
    void appGateDenyCasesAuditAndDoNotMutateBusinessState() throws Exception {
        fixture.allStatusReservations(BUSINESS_DATE);

        jdbc.update("delete from tenant_app_entitlements where tenant_id = ? and app_key = 'reservation_queue'", TENANT_ID);
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", BUSINESS_DATE.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("TENANT_APP_NOT_ENABLED"))
            .andExpect(jsonPath("$.error.messageKey").value("appgate.tenant_app_not_enabled"));
        assertAppGateDeniedWithoutBusinessMutation("TENANT_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.allStatusReservations(BUSINESS_DATE);
        jdbc.update("""
            update store_app_settings
            set is_enabled = false,
                updated_at = now()
            where tenant_id = ? and store_id = ? and app_key = 'reservation_queue'
            """, TENANT_ID, STORE_ID);
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", BUSINESS_DATE.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("STORE_APP_NOT_ENABLED"))
            .andExpect(jsonPath("$.error.messageKey").value("appgate.store_app_not_enabled"));
        assertAppGateDeniedWithoutBusinessMutation("STORE_APP_NOT_ENABLED");

        fixture.reset();
        fixture.createBaseStore();
        fixture.allStatusReservations(BUSINESS_DATE);
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.create"), Set.of(STORE_ID)));
        mockMvc.perform(get(ENDPOINT, STORE_ID).param("businessDate", BUSINESS_DATE.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"))
            .andExpect(jsonPath("$.error.messageKey").value("appgate.permission_denied"));
        assertAppGateDeniedWithoutBusinessMutation("PERMISSION_DENIED");
    }

    private void assertAppGateDeniedWithoutBusinessMutation(String expectedReason) {
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.countWhere("""
            select count(*) from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and operator_user_id = ?
              and operator_role = 'staff'
              and after_json ->> 'denyReason' = ?
              and after_json ->> 'requiredPermission' = 'reservation.today_view'
            """, TENANT_ID, STORE_ID, ACTOR_ID, expectedReason)).isEqualTo(1);
        assertThat(fixture.scalarString("select status from reservations where id = ?", CONFIRMED_ID)).isEqualTo("confirmed");
    }

    private void assertReadOnlyBoundary() {
        assertThat(fixture.count("idempotency_records")).isEqualTo(0);
        assertThat(fixture.count("business_events")).isEqualTo(0);
        assertThat(fixture.count("state_transition_logs")).isEqualTo(0);
        assertThat(fixture.count("audit_logs")).isEqualTo(0);
        assertThat(fixture.count("app_gate_audit_logs")).isEqualTo(0);
        assertThat(fixture.count("queue_tickets")).isEqualTo(0);
        assertThat(fixture.count("seatings")).isEqualTo(0);
        assertThat(fixture.count("table_locks")).isEqualTo(0);
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
                values (?, 'tenant-today-view-it', 'Today View Tenant', 'active', 'en-SG')
                """,
                TENANT_ID
            );
            jdbc.update(
                """
                insert into stores (
                    id, tenant_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, 'store-today-view-it', 'Today View Store', 'active',
                    'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
                """,
                STORE_ID,
                TENANT_ID
            );
            customer(CUSTOMER_PHONE_ID, "C-TV-PHONE", "Phone Guest", "VIP", "+6591234567");
            customer(CUSTOMER_NO_PHONE_ID, "C-TV-NOPHONE", "No Phone Guest", null, null);
            enableReservationQueueApp();
        }

        void allStatusReservations(LocalDate businessDate) {
            reservation(ARRIVED_ID, CUSTOMER_NO_PHONE_ID, "R-TV-ARRIVED", "arrived", businessDate, -60, 0, "Arrived note");
            reservation(SEATED_ID, CUSTOMER_PHONE_ID, "R-TV-SEATED", "seated", businessDate, 0, -1800, "Seated note");
            reservation(CONFIRMED_ID, CUSTOMER_PHONE_ID, "R-TV-CONFIRMED", "confirmed", businessDate, 30, -900, "Confirmed note");
            reservation(CANCELLED_ID, CUSTOMER_NO_PHONE_ID, "R-TV-CANCELLED", "cancelled", businessDate, 90, 0, "Cancelled note");
            reservation(NO_SHOW_ID, CUSTOMER_NO_PHONE_ID, "R-TV-NOSHOW", "no_show", businessDate, 120, 0, "No-show note");
            reservation(COMPLETED_ID, CUSTOMER_PHONE_ID, "R-TV-COMPLETED", "completed", businessDate, 150, 0, "Completed note");
            reservation(DRAFT_ID, CUSTOMER_PHONE_ID, "R-TV-DRAFT", "draft", businessDate, 180, 0, "Draft note");
        }

        void reservation(
            UUID reservationId,
            UUID customerId,
            String reservationCode,
            String status,
            LocalDate businessDate,
            int startOffsetMinutes,
            int createdOffsetSeconds,
            String note
        ) {
            Instant start = Instant.parse(businessDate + "T03:00:00Z").plusSeconds(startOffsetMinutes * 60L);
            Instant end = start.plusSeconds(90 * 60L);
            Instant holdUntil = start.plusSeconds(15 * 60L);
            Instant createdAt = Instant.parse(businessDate + "T01:00:00Z").plusSeconds(createdOffsetSeconds);
            jdbc.update(
                """
                insert into reservations (
                    id, tenant_id, store_id, customer_id, reservation_code, party_size,
                    business_date, reserved_start_at, reserved_end_at, hold_until_at,
                    status, source_channel, note, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, 4, ?, ?, ?, ?, ?, 'staff', ?, ?, ?)
                """,
                reservationId,
                TENANT_ID,
                STORE_ID,
                customerId,
                reservationCode,
                businessDate,
                utc(start),
                utc(end),
                utc(holdUntil),
                status,
                note,
                utc(createdAt),
                utc(createdAt)
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

        private void customer(UUID customerId, String customerCode, String displayName, String nickname, String phoneE164) {
            jdbc.update(
                """
                insert into customers (
                    id, tenant_id, customer_code, customer_type, display_name,
                    nickname, phone_e164, status
                )
                values (?, ?, ?, 'regular', ?, ?, ?, 'active')
                """,
                customerId,
                TENANT_ID,
                customerCode,
                displayName,
                nickname,
                phoneE164
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
