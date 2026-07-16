package com.rpb.reservation.auth.integration;

import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_STORE_ID;
import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReservationMealPeriodMigrationTest {
    private static final AuthPostgresTestDatabase DATABASE = AuthPostgresTestDatabase.startWithValidationStore();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void createsReservationMealPeriodSeedAndStoreOverrideTables() {
        assertThat(tableExists("platform_reservation_meal_period_seeds")).isTrue();
        assertThat(tableExists("store_reservation_meal_period_settings")).isTrue();
        assertThat(tableExists("store_reservation_meal_periods")).isTrue();

        assertThat(countWhere("""
            select count(*)
            from platform_reservation_meal_period_seeds
            where period_key = 'lunch'
              and display_name = '午餐'
              and start_local_time = time '11:00'
              and end_local_time = time '15:00'
              and crosses_next_day = false
              and slot_interval_minutes = 30
              and status = 'active'
              and deleted_at is null
            """)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*)
            from platform_reservation_meal_period_seeds
            where period_key = 'dinner'
              and display_name = '晚餐'
              and start_local_time = time '17:00'
              and end_local_time = time '00:30'
              and crosses_next_day = true
              and slot_interval_minutes = 30
              and status = 'active'
              and deleted_at is null
            """)).isEqualTo(1);

        assertThat(indexExists("ux_platform_reservation_meal_period_key_active")).isTrue();
        assertThat(indexExists("ux_store_reservation_meal_period_settings_scope")).isTrue();
        assertThat(indexExists("ux_store_reservation_meal_period_key_active")).isTrue();
        assertThat(constraintExists("fk_store_reservation_meal_period_settings_store_scope")).isTrue();
        assertThat(constraintExists("fk_store_reservation_meal_periods_store_scope")).isTrue();

        assertThat(countWhere("""
            insert into store_reservation_meal_period_settings (tenant_id, store_id, use_platform_seed)
            values (?, ?, false)
            on conflict (tenant_id, store_id) do update
            set use_platform_seed = excluded.use_platform_seed
            returning 1
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username = 'sysadmin'
              and permission.permission_code = 'platform.reservation_meal_period.manage'
              and permission.deleted_at is null
            """)).isEqualTo(1);
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(DATABASE.jdbcUrl());
        dataSource.setUsername(DATABASE.username());
        dataSource.setPassword(DATABASE.password());
        return dataSource;
    }

    private static boolean tableExists(String tableName) {
        return countWhere("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name = ?
            """, tableName) == 1;
    }

    private static boolean indexExists(String indexName) {
        return countWhere("""
            select count(*)
            from pg_indexes
            where schemaname = 'public'
              and indexname = ?
            """, indexName) == 1;
    }

    private static boolean constraintExists(String constraintName) {
        return countWhere("""
            select count(*)
            from pg_constraint
            where conname = ?
            """, constraintName) == 1;
    }

    private static int countWhere(String sql, Object... args) {
        return JDBC.queryForObject(sql, Integer.class, args);
    }
}
