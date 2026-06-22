package com.rpb.reservation.cleaning.integration;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class CleaningCompleteIntegrationFixture {
    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000201");
    static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000201");
    static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000202");
    static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000201");
    static final UUID AREA_ID = UUID.fromString("31000000-0000-0000-0000-000000000201");

    static final UUID TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000201");
    static final UUID GROUP_MEMBER_TABLE_1_ID = UUID.fromString("40000000-0000-0000-0000-000000000202");
    static final UUID GROUP_MEMBER_TABLE_2_ID = UUID.fromString("40000000-0000-0000-0000-000000000203");
    static final UUID AVAILABLE_TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000204");
    static final UUID CLEANING_TABLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000205");
    static final UUID TABLE_GROUP_ID = UUID.fromString("50000000-0000-0000-0000-000000000201");
    static final UUID INVALID_TABLE_GROUP_ID = UUID.fromString("50000000-0000-0000-0000-000000000202");

    static final UUID TABLE_SEATING_ID = UUID.fromString("70000000-0000-0000-0000-000000000201");
    static final UUID GROUP_SEATING_ID = UUID.fromString("70000000-0000-0000-0000-000000000202");
    static final UUID MISSING_RESOURCE_SEATING_ID = UUID.fromString("70000000-0000-0000-0000-000000000203");
    static final UUID TABLE_CLEANING_ID = UUID.fromString("80000000-0000-0000-0000-000000000201");
    static final UUID GROUP_CLEANING_ID = UUID.fromString("80000000-0000-0000-0000-000000000202");

    private final JdbcTemplate jdbc;

    CleaningCompleteIntegrationFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset() {
        jdbc.execute("truncate table tenants cascade");
    }

    void createBaseStore() {
        jdbc.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'tenant-cleaning-it', 'Cleaning Integration Tenant', 'active', 'en-SG')
            """,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'store-cleaning-it', 'Cleaning Integration Store', 'active',
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
            values (?, ?, 'store-cleaning-it-other', 'Cleaning Integration Other Store', 'active',
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
        table(TABLE_ID, "C1", 1, 4, "occupied");
        table(GROUP_MEMBER_TABLE_1_ID, "CG1", 2, 4, "occupied");
        table(GROUP_MEMBER_TABLE_2_ID, "CG2", 2, 4, "occupied");
        table(AVAILABLE_TABLE_ID, "CA", 1, 4, "available");
        table(CLEANING_TABLE_ID, "CC", 1, 4, "cleaning");
        tableGroup(TABLE_GROUP_ID, "CG-OK", "active", 4, 8);
        tableGroup(INVALID_TABLE_GROUP_ID, "CG-BAD", "inactive", 4, 8);
        groupMember(TABLE_GROUP_ID, GROUP_MEMBER_TABLE_1_ID, "left");
        groupMember(TABLE_GROUP_ID, GROUP_MEMBER_TABLE_2_ID, "right");
    }

    void createOccupiedTableSeating(UUID seatingId, UUID tableId) {
        createWalkInSeating(seatingId, "dining_table", tableId, null, "occupied", "active");
    }

    void createOccupiedTableGroupSeating(UUID seatingId, UUID tableGroupId) {
        createWalkInSeating(seatingId, "table_group", null, tableGroupId, "occupied", "active");
    }

    void createSeatingWithoutResource(UUID seatingId) {
        createWalkIn(seatingId);
        jdbc.update(
            """
            insert into seatings (
                id, tenant_id, store_id, walk_in_id, seating_code, party_size_snapshot,
                status, seated_at
            )
            values (?, ?, ?, ?, ?, 2, 'occupied', now())
            """,
            seatingId,
            TENANT_ID,
            STORE_ID,
            walkInId(seatingId),
            "SEAT-" + seatingId
        );
    }

    void createActiveTableCleaning(UUID cleaningId, UUID seatingId, UUID tableId) {
        createWalkInSeating(seatingId, "dining_table", tableId, null, "cleaning_triggered", "released");
        jdbc.update("update dining_tables set status = 'cleaning' where id = ?", tableId);
        cleaning(cleaningId, seatingId, "dining_table", tableId, null, "cleaning");
    }

    void createActiveTableGroupCleaning(UUID cleaningId, UUID seatingId, UUID tableGroupId) {
        createWalkInSeating(seatingId, "table_group", null, tableGroupId, "cleaning_triggered", "released");
        jdbc.update("update dining_tables set status = 'cleaning' where id in (?, ?)", GROUP_MEMBER_TABLE_1_ID, GROUP_MEMBER_TABLE_2_ID);
        cleaning(cleaningId, seatingId, "table_group", null, tableGroupId, "cleaning");
    }

    void createReleasedCleaning(UUID cleaningId, UUID seatingId, UUID tableId) {
        createWalkInSeating(seatingId, "dining_table", tableId, null, "cleaning_triggered", "released");
        jdbc.update("update dining_tables set status = 'available' where id = ?", tableId);
        cleaning(cleaningId, seatingId, "dining_table", tableId, null, "released");
    }

    void createInvalidTableGroupCleaning(UUID cleaningId, UUID seatingId) {
        createWalkInSeating(seatingId, "table_group", null, INVALID_TABLE_GROUP_ID, "cleaning_triggered", "released");
        cleaning(cleaningId, seatingId, "table_group", null, INVALID_TABLE_GROUP_ID, "cleaning");
    }

    void createActiveCleaningForResource(UUID cleaningId, UUID seatingId, UUID tableId) {
        cleaning(cleaningId, seatingId, "dining_table", tableId, null, "cleaning");
    }

    void idempotencyRecord(
        String action,
        String key,
        String requestHash,
        String status,
        UUID cleaningId,
        UUID seatingId,
        String resourceType,
        UUID resourceId,
        String currentTableStatus,
        String cleaningStatus
    ) {
        String snapshot = switch (status) {
            case "completed" -> """
                {"cleaningId":"%s","seatingId":"%s","resourceType":"%s","resourceId":"%s","currentTableStatus":"%s","cleaningStatus":"%s"}
                """.formatted(cleaningId, seatingId, resourceType, resourceId, currentTableStatus, cleaningStatus).trim();
            case "failed" -> "{\"failure_reason\":\"repository_save_failed\"}";
            default -> null;
        };
        jdbc.update(
            """
            insert into idempotency_records (
                id, tenant_id, store_id, idempotency_key, source, action,
                target_type, target_id, request_hash, response_snapshot, status, expires_at
            )
            values (gen_random_uuid(), ?, ?, ?, 'staff', ?, ?, ?, ?, ?::jsonb, ?, now() + interval '30 minutes')
            """,
            TENANT_ID,
            STORE_ID,
            key,
            action,
            cleaningId == null ? null : "cleaning",
            cleaningId,
            requestHash,
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

    private void createWalkInSeating(
        UUID seatingId,
        String resourceType,
        UUID tableId,
        UUID tableGroupId,
        String seatingStatus,
        String resourceStatus
    ) {
        createWalkIn(seatingId);
        jdbc.update(
            """
            insert into seatings (
                id, tenant_id, store_id, walk_in_id, seating_code, party_size_snapshot,
                status, seated_at
            )
            values (?, ?, ?, ?, ?, 2, ?, now())
            """,
            seatingId,
            TENANT_ID,
            STORE_ID,
            walkInId(seatingId),
            "SEAT-" + seatingId,
            seatingStatus
        );
        jdbc.update(
            """
            insert into seating_resources (
                id, tenant_id, store_id, seating_id, resource_type, table_id,
                table_group_id, assigned_at, released_at, status
            )
            values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now(), ?, ?)
            """,
            TENANT_ID,
            STORE_ID,
            seatingId,
            resourceType,
            tableId,
            tableGroupId,
            "released".equals(resourceStatus) ? OffsetDateTime.now() : null,
            resourceStatus
        );
    }

    private void createWalkIn(UUID seatingId) {
        jdbc.update(
            """
            insert into walk_ins (
                id, tenant_id, store_id, walk_in_code, party_size, business_date,
                arrived_at, status
            )
            values (?, ?, ?, ?, 2, current_date, now(), 'seated')
            """,
            walkInId(seatingId),
            TENANT_ID,
            STORE_ID,
            "WI-" + seatingId
        );
    }

    private void cleaning(UUID cleaningId, UUID seatingId, String resourceType, UUID tableId, UUID tableGroupId, String status) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
            """
            insert into cleanings (
                id, tenant_id, store_id, seating_id, resource_type, table_id,
                table_group_id, status, started_at, completed_at, released_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            cleaningId,
            TENANT_ID,
            STORE_ID,
            seatingId,
            resourceType,
            tableId,
            tableGroupId,
            status,
            status.equals("pending") ? null : now,
            status.equals("completed") || status.equals("released") ? now : null,
            status.equals("released") ? now : null
        );
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

    private static UUID walkInId(UUID seatingId) {
        String value = seatingId.toString().replace("70000000", "60000000");
        return UUID.fromString(value);
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
