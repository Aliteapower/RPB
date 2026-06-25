package com.rpb.reservation.platform.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformTenantAdminAccountRepository {
    private static final String TENANT_ADMIN = "tenant_admin";
    private static final String TENANT_ADMIN_MANAGE = "tenant.admin.manage";

    private final JdbcTemplate jdbc;

    public PlatformTenantAdminAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertTenantAdminAccount(
        UUID tenantId,
        String username,
        String displayName,
        String passwordHash
    ) {
        Optional<UUID> existingAccountId = findTenantAdminAccountId(tenantId);
        UUID accountId = existingAccountId.orElseGet(UUID::randomUUID);
        if (existingAccountId.isPresent()) {
            updateTenantAdminAccount(accountId, username, displayName, passwordHash);
        } else {
            insertTenantAdminAccount(accountId, tenantId, username, displayName, passwordHash);
        }
        ensureTenantAdminRole(accountId);
        ensurePermission(accountId, TENANT_ADMIN_MANAGE);
    }

    private Optional<UUID> findTenantAdminAccountId(UUID tenantId) {
        return jdbc.query(
            """
            select id
            from auth_accounts
            where tenant_id = ?
              and actor_type = 'tenant_admin'
              and deleted_at is null
            order by created_at, id
            limit 1
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            tenantId
        ).stream().findFirst();
    }

    private void insertTenantAdminAccount(
        UUID accountId,
        UUID tenantId,
        String username,
        String displayName,
        String passwordHash
    ) {
        jdbc.update(
            """
            insert into auth_accounts (
                id, tenant_id, username, display_name, actor_type,
                status, password_hash, password_algo
            )
            values (?, ?, ?, ?, 'tenant_admin', 'active', ?, 'bcrypt-lowercase-v1')
            """,
            accountId,
            tenantId,
            username,
            displayName,
            passwordHash
        );
    }

    private void updateTenantAdminAccount(
        UUID accountId,
        String username,
        String displayName,
        String passwordHash
    ) {
        if (passwordHash == null) {
            jdbc.update(
                """
                update auth_accounts
                set username = ?,
                    display_name = ?,
                    status = 'active',
                    updated_at = now(),
                    version = version + 1
                where id = ?
                """,
                username,
                displayName,
                accountId
            );
            return;
        }

        jdbc.update(
            """
            update auth_accounts
            set username = ?,
                display_name = ?,
                status = 'active',
                password_hash = ?,
                password_algo = 'bcrypt-lowercase-v1',
                failed_login_count = 0,
                locked_until_at = null,
                updated_at = now(),
                version = version + 1
            where id = ?
            """,
            username,
            displayName,
            passwordHash,
            accountId
        );
    }

    private void ensureTenantAdminRole(UUID accountId) {
        jdbc.update(
            """
            insert into auth_account_roles (account_id, role_code)
            select ?, ?
            where not exists (
                select 1
                from auth_account_roles existing
                where existing.account_id = ?
                  and existing.role_code = ?
                  and existing.deleted_at is null
            )
            """,
            accountId,
            TENANT_ADMIN,
            accountId,
            TENANT_ADMIN
        );
    }

    private void ensurePermission(UUID accountId, String permissionCode) {
        jdbc.update(
            """
            insert into auth_account_permissions (account_id, permission_code)
            select ?, ?
            where not exists (
                select 1
                from auth_account_permissions existing
                where existing.account_id = ?
                  and existing.permission_code = ?
                  and existing.deleted_at is null
            )
            """,
            accountId,
            permissionCode,
            accountId,
            permissionCode
        );
    }
}
