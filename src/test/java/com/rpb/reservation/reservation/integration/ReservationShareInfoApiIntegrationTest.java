package com.rpb.reservation.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ReservationShareInfoApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/reservations/{reservationId}/share-info";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001201");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001201");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001202");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000001201");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000001201");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000001201");
    private static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000001201");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000001201");
    private static final UUID PREASSIGNMENT_ID = UUID.fromString("80000000-0000-0000-0000-000000001201");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant RESERVED_START_AT = Instant.parse("2030-06-20T03:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

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
        jdbc.execute("truncate table tenants cascade");
        createBaseData();
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.today_view"), Set.of(STORE_ID)));
    }

    @AfterEach
    void clearActor() {
        actorProvider.clear();
    }

    @Test
    void returnsPlainTextShareInfoWithoutMutationOrExternalSendLink() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID, RESERVATION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.shareInfo.reservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.shareInfo.reservationNo").value("R-SHARE-0007"))
            .andExpect(jsonPath("$.shareInfo.channel").value("manual_copy"))
            .andExpect(jsonPath("$.shareInfo.customerMaskedPhone").value("****4567"))
            .andExpect(jsonPath("$.shareInfo.customerPhoneAvailable").value(true))
            .andExpect(jsonPath("$.shareInfo.canOpenWhatsAppLink").value(false))
            .andExpect(jsonPath("$.shareInfo.whatsappLink").doesNotExist())
            .andExpect(jsonPath("$.shareInfo.shareText").value(org.hamcrest.Matchers.containsString("门店：食刻订位中心")))
            .andExpect(jsonPath("$.shareInfo.shareText").value(org.hamcrest.Matchers.containsString("时间：20-06-2030 11:30")))
            .andExpect(jsonPath("$.shareInfo.shareText").value(org.hamcrest.Matchers.containsString("桌位：A01")))
            .andExpect(jsonPath("$.shareInfo.shareText").value(org.hamcrest.Matchers.containsString("保留：15分钟")))
            .andExpect(jsonPath("$.shareInfo.shareText").value(org.hamcrest.Matchers.containsString("地图：https://maps.app.goo.gl/rpb")));

        assertReadOnlyBoundary();
    }

    @Test
    void rejectsWrongStoreScopeBeforeReturningReservationData() throws Exception {
        actorProvider.set(actor(Set.of("store_staff"), Set.of("reservation.today_view"), Set.of(STORE_ID)));

        mockMvc.perform(get(ENDPOINT, OTHER_STORE_ID, RESERVATION_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("STORE_ACCESS_DENIED"));

        assertReadOnlyBoundary();
    }

    @Test
    void appGateDenialAuditsAndDoesNotMutateBusinessState() throws Exception {
        actorProvider.set(actor(Set.of("store_staff"), Set.of(), Set.of(STORE_ID)));

        mockMvc.perform(get(ENDPOINT, STORE_ID, RESERVATION_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

        assertThat(countWhere("""
            select count(*)
            from app_gate_audit_logs
            where tenant_id = ?
              and store_id = ?
              and app_key = 'reservation_queue'
              and action = 'APP_GATE_DENIED'
              and after_json ->> 'requiredPermission' = 'reservation.today_view'
            """, TENANT_ID, STORE_ID)).isEqualTo(1);
        assertThat(count("idempotency_records")).isEqualTo(0);
        assertThat(count("business_events")).isEqualTo(0);
        assertThat(count("state_transition_logs")).isEqualTo(0);
        assertThat(count("audit_logs")).isEqualTo(0);
    }

    private void assertReadOnlyBoundary() {
        assertThat(count("idempotency_records")).isEqualTo(0);
        assertThat(count("business_events")).isEqualTo(0);
        assertThat(count("state_transition_logs")).isEqualTo(0);
        assertThat(count("audit_logs")).isEqualTo(0);
        assertThat(count("queue_tickets")).isEqualTo(0);
        assertThat(count("seatings")).isEqualTo(0);
        assertThat(count("table_locks")).isEqualTo(0);
        assertThat(countWhere("select count(*) from reservations where id = ? and status = 'confirmed'", RESERVATION_ID))
            .isEqualTo(1);
    }

    private void createBaseData() {
        jdbc.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'tenant-share-it', 'Share Tenant', 'active', 'en-SG')
            """,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency,
                share_display_name, share_address, google_map_url,
                share_contact_phone, reservation_share_note, reservation_share_template
            )
            values (?, ?, 'store-share-it', 'Reservation Store', 'active',
                'Asia/Singapore', 'en-SG', 'DD-MM-YYYY', 'HH:mm', 'SGD',
                '食刻订位中心', '1 Example Road', 'https://maps.app.goo.gl/rpb',
                '6333 1234', '请提前 10 分钟到店',
                '门店：{{storeName}}\n编号：{{reservationNo}}\n时间：{{reservationDate}} {{reservationTime}}\n人数：{{partySize}}\n桌位：{{tableCode}}\n保留：{{holdMinutes}}分钟\n联系人：{{contactName}}\n电话：{{maskedPhone}}\n地址：{{storeAddress}}\n地图：{{googleMapUrl}}\n提示：{{arrivalNote}}\n门店电话：{{storePhone}}')
            """,
            STORE_ID,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'store-share-other', 'Other Store', 'active',
                'Asia/Singapore', 'en-SG', 'DD-MM-YYYY', 'HH:mm', 'SGD')
            """,
            OTHER_STORE_ID,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into customers (
                id, tenant_id, customer_code, customer_type, display_name,
                nickname, phone_e164, status
            )
            values (?, ?, 'C-SHARE', 'regular', 'Ada Guest', 'VIP', '+6591234567', 'active')
            """,
            CUSTOMER_ID,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into reservations (
                id, tenant_id, store_id, customer_id, reservation_code, party_size,
                business_date, reserved_start_at, reserved_end_at, hold_until_at,
                status, source_channel, note
            )
            values (?, ?, ?, ?, 'R-SHARE-0007', 4, ?, ?, ?, ?, 'confirmed', 'staff', null)
            """,
            RESERVATION_ID,
            TENANT_ID,
            STORE_ID,
            CUSTOMER_ID,
            BUSINESS_DATE,
            utc(RESERVED_START_AT),
            utc(RESERVED_START_AT.plusSeconds(90 * 60L)),
            utc(RESERVED_START_AT.plusSeconds(15 * 60L))
        );
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
                capacity_min, capacity_max, status, is_combinable
            )
            values (?, ?, ?, ?, 'A01', 'A01', 2, 4, 'available', true)
            """,
            TABLE_ID,
            TENANT_ID,
            STORE_ID,
            AREA_ID
        );
        jdbc.update(
            """
            insert into reservation_preassignments (
                id, tenant_id, store_id, reservation_id, resource_type, table_id,
                status, preassigned_at
            )
            values (?, ?, ?, ?, 'dining_table', ?, 'active', now())
            """,
            PREASSIGNMENT_ID,
            TENANT_ID,
            STORE_ID,
            RESERVATION_ID,
            TABLE_ID
        );
        enableReservationQueueApp(STORE_ID);
        enableReservationQueueApp(OTHER_STORE_ID);
    }

    private void enableReservationQueueApp(UUID storeId) {
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
            storeId
        );
    }

    private int count(String tableName) {
        return jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    private int countWhere(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions, Set<UUID> storeIds) {
        return CurrentActor.storeStaff(TENANT_ID, ACTOR_ID, "staff", roles, permissions, storeIds);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
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
