package com.rpb.reservation.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort;
import com.rpb.reservation.audit.domain.BusinessEvent;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
class ReservationTableAssignmentApiIntegrationTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009901");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000009901");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000009901");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000009901");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000009901");
    private static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000009901");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000009901");
    private static final UUID SMALL_TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000009902");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");
    private static final Instant START = Instant.parse("2030-06-20T03:30:00Z");
    private static final Instant END = Instant.parse("2030-06-20T05:00:00Z");
    private static final String ASSIGNMENT_ENDPOINT =
        "/api/v1/stores/{storeId}/reservations/{reservationId}/table-assignment";
    private static final String ASSIGNABLE_ENDPOINT =
        "/api/v1/stores/{storeId}/reservations/{reservationId}/assignable-tables";
    private static final String CHECK_IN_ENDPOINT =
        "/api/v1/stores/{storeId}/reservations/{reservationId}/check-in";
    private static final String DIRECT_SEATING_ENDPOINT =
        "/api/v1/stores/{storeId}/reservations/{reservationId}/seating/direct";
    private static JdbcTemplate cleanupJdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    @SpyBean(reset = MockReset.AFTER)
    private BusinessEventRepositoryPort businessEventRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        Map<String, String> pointer = pointerSettings();
        registry.add(
            "spring.datasource.url",
            () -> "jdbc:postgresql://127.0.0.1:" + pointer.get("port") + "/postgres?stringtype=unspecified"
        );
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @BeforeEach
    void setUp() {
        cleanupJdbc = jdbc;
        cleanupTenant();
        createFixture();
        actorProvider.set(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of(
                "table.view",
                "reservation.create",
                "reservation.today_view",
                "reservation.check_in",
                "reservation.seat"
            ),
            Set.of(STORE_ID)
        ));
    }

    @AfterEach
    void clearFixture() {
        actorProvider.clear();
        cleanupTenant();
    }

    @AfterAll
    static void ensureFixtureRemoved() {
        if (cleanupJdbc != null) {
            cleanupJdbc.update("delete from tenants where id = ?", TENANT_ID);
        }
    }

    @Test
    void assignmentPersistsOnceAndImmediatelyFeedsTodayAndShareViews() throws Exception {
        mockMvc.perform(get(ASSIGNABLE_ENDPOINT, STORE_ID, RESERVATION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tables.length()").value(1))
            .andExpect(jsonPath("$.tables[0].tableId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.tables[0].tableCode").value("A01"));

        String body = "{\"tableId\":\"%s\"}".formatted(TABLE_ID);
        mockMvc.perform(put(ASSIGNMENT_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-integration-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableCode").value("A01"))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(put(ASSIGNMENT_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-integration-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableCode").value("A01"))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertThat(count("reservation_preassignments")).isEqualTo(1);
        assertThat(scalar("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
        assertThat(count("queue_tickets")).isZero();
        assertThat(count("seatings")).isZero();
        assertThat(countWhere(
            "select count(*) from business_events where tenant_id = ? and event_type = 'reservation.table_assigned'",
            TENANT_ID
        ))
            .isEqualTo(1);
        assertThat(countWhere(
            "select count(*) from audit_logs where tenant_id = ? and operation_code = 'reservation.table_assign'",
            TENANT_ID
        ))
            .isEqualTo(1);

        mockMvc.perform(get("/api/v1/stores/{storeId}/reservations/today", STORE_ID)
                .param("businessDate", BUSINESS_DATE.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].assignedResourceId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.items[0].assignedResourceCode").value("A01"));

        mockMvc.perform(get("/api/v1/stores/{storeId}/reservations/{reservationId}/share-info", STORE_ID, RESERVATION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shareInfo.shareText").value(org.hamcrest.Matchers.containsString("桌位：A01")));

        String token = scalar(
            "select token from reservation_public_share_tokens where tenant_id = ? and store_id = ? and reservation_id = ?",
            TENANT_ID,
            STORE_ID,
            RESERVATION_ID
        );
        actorProvider.clear();
        mockMvc.perform(get("/api/v1/public/reservation-shares/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.share.tableCode").value("A01"))
            .andExpect(jsonPath("$.share.tablePending").value(false));
    }

    @Test
    void eventFailureRollsBackAssignmentAndKeepsReservationConfirmed() throws Exception {
        doThrow(new IllegalStateException("forced table assignment event failure"))
            .when(businessEventRepository)
            .append(any(StoreScope.class), any(BusinessEvent.class));

        mockMvc.perform(put(ASSIGNMENT_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-integration-event-failure")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"%s\"}".formatted(TABLE_ID)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("BUSINESS_EVENT_WRITE_FAILED"));

        assertThat(count("reservation_preassignments")).isZero();
        assertThat(scalar("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
        assertThat(count("business_events")).isZero();
        assertThat(count("audit_logs")).isZero();
        assertThat(countWhere(
            "select count(*) from idempotency_records where tenant_id = ? and idempotency_key = ?",
            TENANT_ID,
            "assign-integration-event-failure"
        )).isZero();
    }

    @Test
    void occupiedTableIsNeitherListedNorAcceptedForAssignment() throws Exception {
        jdbc.update("update dining_tables set status = 'occupied' where id = ?", TABLE_ID);

        mockMvc.perform(get(ASSIGNABLE_ENDPOINT, STORE_ID, RESERVATION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tables.length()").value(0));

        mockMvc.perform(put(ASSIGNMENT_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-occupied-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignmentBody(TABLE_ID)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("TABLE_NOT_AVAILABLE"));

        assertThat(count("reservation_preassignments")).isZero();
        assertThat(scalar("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
    }

    @Test
    void publicReservationAssignmentCheckInAndSeatingConsumesOwnershipOnce() throws Exception {
        mockMvc.perform(put(ASSIGNMENT_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "assign-before-seating")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignmentBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tableCode").value("A01"));

        assertThat(scalar("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("confirmed");
        assertThat(scalar(
            "select status from reservation_preassignments where reservation_id = ?",
            RESERVATION_ID
        )).isEqualTo("active");

        mockMvc.perform(post(CHECK_IN_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "check-in-before-seating")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody(START.minusSeconds(300))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("arrived"));

        mockMvc.perform(post(DIRECT_SEATING_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "seat-preassigned-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatingBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reservationStatus").value("seated"))
            .andExpect(jsonPath("$.resourceId").value(TABLE_ID.toString()))
            .andExpect(jsonPath("$.idempotency.replayed").value(false));

        mockMvc.perform(post(DIRECT_SEATING_ENDPOINT, STORE_ID, RESERVATION_ID)
                .header("Idempotency-Key", "seat-preassigned-table")
                .contentType(MediaType.APPLICATION_JSON)
                .content(seatingBody(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.idempotency.replayed").value(true));

        assertThat(scalar("select status from reservations where id = ?", RESERVATION_ID)).isEqualTo("seated");
        assertThat(scalar("select status from dining_tables where id = ?", TABLE_ID)).isEqualTo("occupied");
        assertThat(scalar(
            "select status from reservation_preassignments where reservation_id = ?",
            RESERVATION_ID
        )).isEqualTo("released");
        assertThat(countWhere(
            "select count(*) from seating_resources where tenant_id = ? and table_id = ? and status = 'active'",
            TENANT_ID,
            TABLE_ID
        )).isEqualTo(1);
        assertThat(countWhere(
            "select count(*) from audit_logs where tenant_id = ? and operation_code = 'reservation.seat' and metadata::text like '%preassignmentStatusAfter%released%'",
            TENANT_ID
        )).isEqualTo(1);
        assertThat(countWhere(
            "select count(*) from idempotency_records where tenant_id = ? and idempotency_key in (?, ?, ?) and status = 'completed'",
            TENANT_ID,
            "assign-before-seating",
            "check-in-before-seating",
            "seat-preassigned-table"
        )).isEqualTo(3);
    }

    private void createFixture() {
        jdbc.update("""
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'tenant-table-assignment-it', 'Table Assignment Tenant', 'active', 'zh-CN')
            """, TENANT_ID);
        jdbc.update("""
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency,
                share_display_name, reservation_share_template
            )
            values (?, ?, 'store-table-assignment-it', 'Table Assignment Store', 'active',
                'Asia/Singapore', 'zh-CN', 'DD-MM-YYYY', 'HH:mm', 'SGD',
                '食刻订位中心', '门店：{{storeName}}\n编号：{{reservationNo}}\n桌位：{{tableCode}}')
            """, STORE_ID, TENANT_ID);
        jdbc.update("""
            insert into customers (
                id, tenant_id, customer_code, customer_type, display_name, phone_e164, status
            ) values (?, ?, 'C-TABLE-ASSIGNMENT', 'regular', 'Public Guest', '+6591234567', 'active')
            """, CUSTOMER_ID, TENANT_ID);
        jdbc.update("""
            insert into reservations (
                id, tenant_id, store_id, customer_id, reservation_code, party_size,
                business_date, reserved_start_at, reserved_end_at, hold_until_at,
                status, source_channel, note
            ) values (?, ?, ?, ?, 'R-TABLE-ASSIGNMENT-1', 2, ?, ?, ?, ?,
                'confirmed', 'public_booking', null)
            """,
            RESERVATION_ID,
            TENANT_ID,
            STORE_ID,
            CUSTOMER_ID,
            BUSINESS_DATE,
            utc(START),
            utc(END),
            utc(START.plusSeconds(900))
        );
        jdbc.update("""
            insert into store_areas (id, tenant_id, store_id, area_code, display_name, status, sort_order)
            values (?, ?, ?, 'MAIN', '大厅', 'active', 1)
            """, AREA_ID, TENANT_ID, STORE_ID);
        jdbc.update("""
            insert into dining_tables (
                id, tenant_id, store_id, area_id, table_code, display_name,
                capacity_min, capacity_max, status, is_combinable
            ) values (?, ?, ?, ?, 'A01', 'A01', 1, 4, 'available', false)
            """, TABLE_ID, TENANT_ID, STORE_ID, AREA_ID);
        jdbc.update("""
            insert into dining_tables (
                id, tenant_id, store_id, area_id, table_code, display_name,
                capacity_min, capacity_max, status, is_combinable
            ) values (?, ?, ?, ?, 'A02', 'A02', 1, 1, 'available', false)
            """, SMALL_TABLE_ID, TENANT_ID, STORE_ID, AREA_ID);
        jdbc.update("""
            insert into tenant_app_entitlements (
                tenant_id, app_key, status, valid_from, config_json, enabled_at
            ) values (?, 'reservation_queue', 'enabled', now(), '{}'::jsonb, now())
            """, TENANT_ID);
        jdbc.update("""
            insert into store_app_settings (
                tenant_id, store_id, app_key, is_enabled, entry_visible, config_json, enabled_at
            ) values (?, ?, 'reservation_queue', true, true, '{}'::jsonb, now())
            """, TENANT_ID, STORE_ID);
    }

    private void cleanupTenant() {
        jdbc.update("delete from reservation_public_share_tokens where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from app_gate_audit_logs where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from state_transition_logs where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from business_events where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from audit_logs where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from idempotency_records where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from reservation_preassignments where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from queue_tickets where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from seating_resources where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from seatings where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from reservations where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from dining_tables where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from store_areas where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from customers where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from store_app_settings where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from tenant_app_entitlements where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from stores where tenant_id = ?", TENANT_ID);
        jdbc.update("delete from tenants where id = ?", TENANT_ID);
    }

    private int count(String table) {
        return countWhere("select count(*) from " + table + " where tenant_id = ?", TENANT_ID);
    }

    private int countWhere(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String scalar(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private static OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String assignmentBody(UUID tableId) {
        return "{\"tableId\":\"%s\"}".formatted(tableId);
    }

    private static String checkInBody(Instant arrivedAt) {
        return """
            {
              "arrivedAt": "%s",
              "reasonCode": "customer_arrived",
              "note": "Guest is waiting at host stand"
            }
            """.formatted(arrivedAt);
    }

    private static String seatingBody(UUID tableId) {
        return """
            {
              "tableId": "%s",
              "tableGroupId": null,
              "overrideReasonCode": null,
              "overrideNote": null,
              "note": null
            }
            """.formatted(tableId);
    }

    private static Map<String, String> pointerSettings() {
        Path pointer = Path.of("target", "local-postgres-current.txt");
        if (!Files.isRegularFile(pointer)) {
            throw new IllegalStateException("local_postgres_pointer_missing: " + pointer.toAbsolutePath());
        }
        try {
            return Files.readAllLines(pointer).stream()
                .filter(line -> line.contains("="))
                .map(line -> line.split("=", 2))
                .collect(Collectors.toUnmodifiableMap(parts -> parts[0], parts -> parts[1]));
        } catch (IOException exception) {
            throw new IllegalStateException("local_postgres_pointer_read_failed", exception);
        }
    }

    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2030-06-20T01:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        TestCurrentActorProvider testCurrentActorProvider() {
            return new TestCurrentActorProvider();
        }
    }

    static final class TestCurrentActorProvider implements CurrentActorProvider {
        private final ThreadLocal<CurrentActor> actor = new ThreadLocal<>();

        void set(CurrentActor value) {
            actor.set(value);
        }

        void clear() {
            actor.remove();
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(actor.get());
        }
    }
}
