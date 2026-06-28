package com.rpb.reservation.auth.integration;

import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_STORE_ID;
import static com.rpb.reservation.auth.integration.AuthPostgresTestDatabase.VALIDATION_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.appgate.domain.AppGateRequiredPermission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthMigrationTest {
    private static final AuthPostgresTestDatabase DATABASE = AuthPostgresTestDatabase.startWithValidationStore();
    private static final JdbcTemplate JDBC = new JdbcTemplate(dataSource());

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @Test
    void createsAuthTablesAndSeedsThreeLocalValidationAccountsWhenValidationStoreExists() {
        assertThat(tableExists("auth_accounts")).isTrue();
        assertThat(tableExists("auth_account_roles")).isTrue();
        assertThat(tableExists("auth_account_permissions")).isTrue();
        assertThat(tableExists("auth_account_store_access")).isTrue();
        assertThat(tableExists("auth_user_sessions")).isTrue();
        assertThat(tableExists("auth_slider_captcha_challenges")).isTrue();
        assertThat(columnExists("tenants", "contact_phone")).isTrue();
        assertThat(columnExists("tenants", "address")).isTrue();
        assertThat(columnExists("tenants", "principal_name")).isTrue();

        assertThat(countWhere("""
            select count(*)
            from auth_accounts
            where tenant_id = ?
              and username in ('sysadmin', '20000000', '1000')
              and password_hash like '$2a$10$%'
              and status = 'active'
            """, VALIDATION_TENANT_ID)).isEqualTo(3);

        assertThat(strings("""
            select role_code
            from auth_account_roles role
            join auth_accounts account on account.id = role.account_id
            where account.username = 'sysadmin'
            order by role_code
            """)).containsExactly("platform_admin", "tenant_admin");

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username = '1000'
            """)).isEqualTo(AppGateRequiredPermission.RESERVATION_QUEUE_ENTRY_PERMISSIONS.size());

        assertThat(countWhere("""
            select count(*)
            from auth_account_store_access access
            join auth_accounts account on account.id = access.account_id
            where account.username in ('sysadmin', '20000000', '1000')
              and access.tenant_id = ?
              and access.store_id = ?
            """, VALIDATION_TENANT_ID, VALIDATION_STORE_ID)).isEqualTo(3);

        assertThat(countWhere("""
            select count(*)
            from tenants
            where id = ?
              and tenant_code = '20000000'
              and display_name = '食刻租户'
              and deleted_at is null
            """, VALIDATION_TENANT_ID)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username = 'sysadmin'
              and permission.permission_code = 'platform.tenant.manage'
              and permission.deleted_at is null
            """)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username = 'sysadmin'
              and permission.permission_code = 'platform.call_screen_ad.manage'
              and permission.deleted_at is null
            """)).isEqualTo(1);

        assertThat(countWhere("""
            select count(*)
            from auth_account_permissions permission
            join auth_accounts account on account.id = permission.account_id
            where account.username = 'sysadmin'
              and permission.permission_code = 'platform.reservation_share_template.manage'
              and permission.deleted_at is null
            """)).isEqualTo(1);
    }

    @Test
    void enforcesActiveAccountUsernameUniquenessAndSessionHashUniqueness() {
        assertThat(countWhere("""
            select count(*)
            from pg_indexes
            where indexname in ('ux_auth_accounts_username_active', 'ux_auth_user_sessions_session_hash')
            """)).isEqualTo(2);
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

    private static boolean columnExists(String tableName, String columnName) {
        return countWhere("""
            select count(*)
            from information_schema.columns
            where table_schema = 'public'
              and table_name = ?
              and column_name = ?
            """, tableName, columnName) == 1;
    }

    private static int countWhere(String sql, Object... args) {
        return JDBC.queryForObject(sql, Integer.class, args);
    }

    private static java.util.List<String> strings(String sql) {
        return JDBC.queryForList(sql, String.class);
    }
}
