package com.rpb.reservation.reservation.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class ReservationCreateIntegrationFixture {
    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000501");
    static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000501");
    static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000502");
    static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000501");
    static final UUID EXISTING_CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000501");
    static final UUID DUPLICATE_CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000502");
    static final UUID CAPACITY_CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000000503");
    static final UUID REPLAY_RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000599");
    static final UUID AREA_ID = UUID.fromString("60000000-0000-0000-0000-000000000501");
    static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000000501");
    static final Instant START_AT = Instant.parse("2030-06-20T03:00:00Z");
    static final Instant END_AT = Instant.parse("2030-06-20T04:30:00Z");
    static final Instant HOLD_UNTIL_AT = Instant.parse("2030-06-20T03:15:00Z");
    static final LocalDate BUSINESS_DATE = LocalDate.parse("2030-06-20");

    private final JdbcTemplate jdbc;

    ReservationCreateIntegrationFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset() {
        jdbc.execute("truncate table tenants cascade");
    }

    void createBaseStore() {
        jdbc.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'tenant-reservation-it', 'Reservation Integration Tenant', 'active', 'en-SG')
            """,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'store-reservation-it', 'Reservation Integration Store', 'active',
                'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
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
            values (?, ?, 'store-reservation-it-other', 'Reservation Integration Other Store', 'active',
                'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
            """,
            OTHER_STORE_ID,
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
        enableReservationQueueApp(STORE_ID);
        enableReservationQueueApp(OTHER_STORE_ID);
        customer(EXISTING_CUSTOMER_ID, "C-EXISTING", null);
        customer(DUPLICATE_CUSTOMER_ID, "C-DUPLICATE", null);
        customer(CAPACITY_CUSTOMER_ID, "C-CAPACITY", null);
        createTableResources();
    }

    void createTableResources() {
        jdbc.update(
            """
            insert into store_areas (
                id, tenant_id, store_id, area_code, display_name, status, sort_order
            )
            values (?, ?, ?, 'A', 'A区', 'active', 1)
            on conflict do nothing
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
            on conflict do nothing
            """,
            TABLE_ID,
            TENANT_ID,
            STORE_ID,
            AREA_ID
        );
    }

    void customer(UUID customerId, String customerCode, String phoneE164) {
        jdbc.update(
            """
            insert into customers (
                id, tenant_id, customer_code, customer_type, display_name,
                nickname, phone_e164, status
            )
            values (?, ?, ?, 'regular', ?, null, ?, 'active')
            """,
            customerId,
            TENANT_ID,
            customerCode,
            customerCode,
            phoneE164
        );
    }

    void activeReservation(UUID reservationId, UUID customerId, String code, int partySize) {
        jdbc.update(
            """
            insert into reservations (
                id, tenant_id, store_id, customer_id, reservation_code, party_size,
                business_date, reserved_start_at, reserved_end_at, hold_until_at,
                status, source_channel, note
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'confirmed', 'staff', null)
            """,
            reservationId,
            TENANT_ID,
            STORE_ID,
            customerId,
            code,
            partySize,
            BUSINESS_DATE,
            utc(START_AT),
            utc(END_AT),
            utc(HOLD_UNTIL_AT)
        );
    }

    void idempotencyRecord(String key, String hash, String status, UUID reservationId, UUID customerId) {
        String snapshot = "completed".equals(status)
            ? """
                {"reservationId":"%s","customerId":"%s","reservationCode":"R-20300620-0099","partySize":4,"businessDate":"%s","reservedStartAt":"%s","reservedEndAt":"%s","holdUntilAt":"%s","status":"confirmed"}
                """.formatted(reservationId, customerId, BUSINESS_DATE, START_AT, END_AT, HOLD_UNTIL_AT).trim()
            : "failed".equals(status) ? "{\"failure_reason\":\"reservation_capacity_insufficient\"}" : null;
        jdbc.update(
            """
            insert into idempotency_records (
                id, tenant_id, store_id, idempotency_key, source, action,
                target_type, target_id, request_hash, response_snapshot, status, expires_at
            )
            values (gen_random_uuid(), ?, ?, ?, 'staff', 'create_reservation',
                ?, ?, ?, ?::jsonb, ?, now() + interval '30 minutes')
            """,
            TENANT_ID,
            STORE_ID,
            key,
            reservationId == null ? null : "reservation",
            reservationId,
            hash,
            snapshot,
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

    OffsetDateTime scalarOffsetDateTime(String sql, Object... args) {
        return jdbc.queryForObject(sql, OffsetDateTime.class, args);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
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
}
