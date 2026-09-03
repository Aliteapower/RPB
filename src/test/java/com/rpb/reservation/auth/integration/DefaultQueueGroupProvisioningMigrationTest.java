package com.rpb.reservation.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DefaultQueueGroupProvisioningMigrationTest {
    private static final String MIGRATION =
        "src/main/resources/db/migration/V046__backfill_default_queue_groups.sql";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000946");
    private static final UUID EMPTY_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000946");
    private static final UUID CUSTOM_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000947");
    private static final UUID INACTIVE_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000948");
    private static final UUID DELETED_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000949");
    private static final UUID SOFT_DELETED_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000000950");

    private static final AuthPostgresTestDatabase DATABASE = AuthPostgresTestDatabase.startWithBaseSchema();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());

    @BeforeAll
    static void migrateFixtures() {
        insertFixtures();
        DATABASE.applyMigration(MIGRATION);
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void backfillsOnlyStoresWithoutAnyQueueGroupHistory() {
        assertThat(groups(EMPTY_STORE_ID)).containsExactly(
            new QueueGroupRow("1-2", 1, 2, "queue.group.1_2", "active", 1, false),
            new QueueGroupRow("3-4", 3, 4, "queue.group.3_4", "active", 2, false),
            new QueueGroupRow("5-6", 5, 6, "queue.group.5_6", "active", 3, false),
            new QueueGroupRow("7+", 7, null, "queue.group.7_plus", "active", 4, false)
        );
        assertThat(groups(CUSTOM_STORE_ID)).containsExactly(
            new QueueGroupRow("custom", 1, null, "queue.group.custom", "active", 9, false)
        );
        assertThat(groups(INACTIVE_STORE_ID)).containsExactly(
            new QueueGroupRow("inactive", 1, null, "queue.group.inactive", "inactive", 9, false)
        );
        assertThat(groups(DELETED_STORE_ID)).containsExactly(
            new QueueGroupRow("deleted", 1, null, "queue.group.deleted", "inactive", 9, true)
        );
        assertThat(groups(SOFT_DELETED_STORE_ID)).isEmpty();
    }

    @Test
    void migrationIsIdempotent() {
        DATABASE.applyMigration(MIGRATION);

        assertThat(countGroups(EMPTY_STORE_ID)).isEqualTo(4);
        assertThat(countGroups(CUSTOM_STORE_ID)).isEqualTo(1);
        assertThat(countGroups(INACTIVE_STORE_ID)).isEqualTo(1);
        assertThat(countGroups(DELETED_STORE_ID)).isEqualTo(1);
        assertThat(countGroups(SOFT_DELETED_STORE_ID)).isZero();
    }

    private static void insertFixtures() {
        JDBC.update("""
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'migration-946', 'Queue Migration Tenant', 'active', 'zh-CN')
            """, TENANT_ID);
        insertStore(EMPTY_STORE_ID, "empty");
        insertStore(CUSTOM_STORE_ID, "custom");
        insertStore(INACTIVE_STORE_ID, "inactive");
        insertStore(DELETED_STORE_ID, "deleted");
        insertStore(SOFT_DELETED_STORE_ID, "soft-deleted-store");
        JDBC.update("update stores set deleted_at = now() where id = ?", SOFT_DELETED_STORE_ID);
        insertGroup(CUSTOM_STORE_ID, "custom", "queue.group.custom", "active", null);
        insertGroup(INACTIVE_STORE_ID, "inactive", "queue.group.inactive", "inactive", null);
        insertGroup(DELETED_STORE_ID, "deleted", "queue.group.deleted", "inactive", "now()");
    }

    private static void insertStore(UUID storeId, String storeCode) {
        JDBC.update("""
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, ?, ?, 'active', 'Asia/Singapore', 'zh-CN', 'DD-MM-YYYY', 'HH:mm', 'SGD')
            """, storeId, TENANT_ID, storeCode, storeCode + " Store");
    }

    private static void insertGroup(
        UUID storeId,
        String groupCode,
        String displayKey,
        String status,
        String deletedAtExpression
    ) {
        String deletedAt = deletedAtExpression == null ? "null" : deletedAtExpression;
        JDBC.update("""
            insert into queue_groups (
                tenant_id, store_id, group_code, min_party_size, max_party_size,
                display_i18n_key, status, sort_order, deleted_at
            )
            values (?, ?, ?, 1, null, ?, ?, 9, %s)
            """.formatted(deletedAt), TENANT_ID, storeId, groupCode, displayKey, status);
    }

    private static List<QueueGroupRow> groups(UUID storeId) {
        return JDBC.query("""
            select group_code, min_party_size, max_party_size, display_i18n_key,
                   status, sort_order, deleted_at is not null
            from queue_groups
            where tenant_id = ? and store_id = ?
            order by sort_order, group_code
            """, (resultSet, rowNum) -> new QueueGroupRow(
            resultSet.getString(1),
            resultSet.getInt(2),
            resultSet.getObject(3, Integer.class),
            resultSet.getString(4),
            resultSet.getString(5),
            resultSet.getInt(6),
            resultSet.getBoolean(7)
        ), TENANT_ID, storeId);
    }

    private static int countGroups(UUID storeId) {
        return JDBC.queryForObject("""
            select count(*) from queue_groups where tenant_id = ? and store_id = ?
            """, Integer.class, TENANT_ID, storeId);
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(DATABASE.jdbcUrl());
        dataSource.setUsername(DATABASE.username());
        dataSource.setPassword(DATABASE.password());
        return dataSource;
    }

    private record QueueGroupRow(
        String groupCode,
        int minPartySize,
        Integer maxPartySize,
        String displayI18nKey,
        String status,
        int sortOrder,
        boolean deleted
    ) {
    }
}
