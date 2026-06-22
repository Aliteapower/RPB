package com.rpb.reservation.walkin.integration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WalkInDirectSeatingIntegrationFixture {
    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000101");
    static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000101");
    static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000102");
    static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000101");
    static final UUID AREA_ID = UUID.fromString("31000000-0000-0000-0000-000000000101");
    static final UUID RECOMMENDED_TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000101");
    static final UUID SECOND_TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000102");
    static final UUID GROUP_MEMBER_TABLE_1_ID = UUID.fromString("40000000-0000-0000-0000-000000000103");
    static final UUID GROUP_MEMBER_TABLE_2_ID = UUID.fromString("40000000-0000-0000-0000-000000000104");
    static final UUID INACTIVE_TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000105");
    static final UUID SMALL_TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000106");
    static final UUID LOCKED_TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000107");
    static final UUID TABLE_GROUP_ID = UUID.fromString("50000000-0000-0000-0000-000000000101");
    static final UUID INVALID_TABLE_GROUP_ID = UUID.fromString("50000000-0000-0000-0000-000000000102");

    private final JdbcTemplate jdbc;

    WalkInDirectSeatingIntegrationFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset() {
        jdbc.execute("truncate table tenants cascade");
    }

    void createBaseStore() {
        jdbc.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'tenant-it', 'Integration Tenant', 'active', 'en-SG')
            """,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'store-it', 'Integration Store', 'active',
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
            values (?, ?, 'store-it-other', 'Integration Other Store', 'active',
                'Asia/Singapore', 'en-SG', 'yyyy-MM-dd', 'HH:mm', 'SGD')
            """,
            OTHER_STORE_ID,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into store_areas (id, tenant_id, store_id, area_code, display_name, status, sort_order)
            values (?, ?, ?, 'main', 'Main Dining Room', 'active', 1)
            """,
            AREA_ID,
            TENANT_ID,
            STORE_ID
        );
        jdbc.update(
            """
            insert into store_policies (
                id, tenant_id, store_id, reservation_hold_minutes, queue_call_hold_minutes,
                expected_dining_minutes, queue_rejoin_policy_code, table_assignment_policy_code,
                effective_from_at
            )
            values (gen_random_uuid(), ?, ?, 15, 3, 90, 'same_group_tail', 'default_capacity_time_area_group', now() - interval '1 day')
            """,
            TENANT_ID,
            STORE_ID
        );
        enableReservationQueueApp(STORE_ID);
        enableReservationQueueApp(OTHER_STORE_ID);
        availableTable(RECOMMENDED_TABLE_ID, "A1", 1, 4);
        availableTable(SECOND_TABLE_ID, "Z9", 1, 4);
        availableTable(GROUP_MEMBER_TABLE_1_ID, "G1", 3, 3);
        availableTable(GROUP_MEMBER_TABLE_2_ID, "G2", 3, 4);
        table(INACTIVE_TABLE_ID, "I1", 1, 4, "inactive");
        availableTable(SMALL_TABLE_ID, "S1", 1, 2);
        availableTable(LOCKED_TABLE_ID, "L1", 1, 4);
        tableGroup(TABLE_GROUP_ID, "G-OK", "active", 3, 6);
        tableGroup(INVALID_TABLE_GROUP_ID, "G-BAD", "inactive", 3, 6);
        groupMember(TABLE_GROUP_ID, GROUP_MEMBER_TABLE_1_ID, "left");
        groupMember(TABLE_GROUP_ID, GROUP_MEMBER_TABLE_2_ID, "right");
    }

    void createActiveLock(UUID tableId, String lockKey) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
            """
            insert into table_locks (
                id, tenant_id, store_id, resource_type, resource_id, lock_key, lock_owner,
                locked_until_at, source_type, source_id, idempotency_key, status, locked_at
            )
            values (gen_random_uuid(), ?, ?, 'dining_table', ?, ?, 'staff',
                ?, 'manual', null, null, 'active', ?)
            """,
            TENANT_ID,
            STORE_ID,
            tableId,
            lockKey,
            now.plusMinutes(10),
            now
        );
    }

    void idempotencyRecord(String key, String hash, String status, UUID walkInId, UUID seatingId, UUID resourceId) {
        String snapshot = switch (status) {
            case "completed" -> """
                {"walkInId":"%s","seatingId":"%s","resourceType":"dining_table","resourceId":"%s","partySizeSnapshot":2}
                """.formatted(walkInId, seatingId, resourceId).trim();
            case "failed" -> "{\"failure_reason\":\"repository_save_failed\"}";
            default -> null;
        };
        jdbc.update(
            """
            insert into idempotency_records (
                id, tenant_id, store_id, idempotency_key, source, action,
                target_type, target_id, request_hash, response_snapshot, status, expires_at
            )
            values (gen_random_uuid(), ?, ?, ?, 'staff', 'seat_walk_in_directly',
                ?, ?, ?, ?::jsonb, ?, now() + interval '30 minutes')
            """,
            TENANT_ID,
            STORE_ID,
            key,
            seatingId == null ? null : "seating",
            seatingId,
            hash,
            snapshot,
            status
        );
    }

    int count(String tableName) {
        return jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
    }

    String scalarString(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    Integer scalarInteger(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void availableTable(UUID tableId, String tableCode, int capacityMin, int capacityMax) {
        table(tableId, tableCode, capacityMin, capacityMax, "available");
    }

    private void table(UUID tableId, String tableCode, int capacityMin, int capacityMax, String status) {
        jdbc.update(
            """
            insert into dining_tables (
                id, tenant_id, store_id, area_id, table_code, display_name,
                capacity_min, capacity_max, status, is_combinable
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, true)
            """,
            tableId,
            TENANT_ID,
            STORE_ID,
            AREA_ID,
            tableCode,
            tableCode,
            capacityMin,
            capacityMax,
            status
        );
    }

    private void tableGroup(UUID groupId, String groupCode, String status, int capacityMin, int capacityMax) {
        jdbc.update(
            """
            insert into table_groups (
                id, tenant_id, store_id, group_code, group_type, status,
                display_name, capacity_min, capacity_max
            )
            values (?, ?, ?, ?, 'fixed', ?, ?, ?, ?)
            """,
            groupId,
            TENANT_ID,
            STORE_ID,
            groupCode,
            status,
            groupCode,
            capacityMin,
            capacityMax
        );
    }

    private void groupMember(UUID groupId, UUID tableId, String role) {
        jdbc.update(
            """
            insert into table_group_members (id, tenant_id, store_id, table_group_id, table_id, member_role)
            values (gen_random_uuid(), ?, ?, ?, ?, ?)
            """,
            TENANT_ID,
            STORE_ID,
            groupId,
            tableId,
            role
        );
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
