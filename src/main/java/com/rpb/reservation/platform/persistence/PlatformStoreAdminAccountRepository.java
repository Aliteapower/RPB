package com.rpb.reservation.platform.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformStoreAdminAccountRepository {
    private static final String STORE_MANAGER_ROLE = "store_manager";
    private static final List<String> STORE_MANAGER_PERMISSIONS = List.of(
        "reservation.create",
        "reservation.check_in",
        "reservation.seat",
        "reservation.today_view",
        "reservation.queue",
        "reservation.cancel",
        "reservation.no_show",
        "reservation.complete",
        "queue.view",
        "queue.call",
        "queue.seat",
        "queue.skip",
        "queue.rejoin",
        "queue.cancel",
        "table.view",
        "table.switch",
        "customer.lookup",
        "walkin.direct_seating.create",
        "walkin.queue.create",
        "cleaning.start",
        "cleaning.complete"
    );

    private final JdbcTemplate jdbc;

    public PlatformStoreAdminAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean upsertStoreManager(
        UUID tenantId,
        UUID storeId,
        String username,
        String displayName,
        String passwordHash
    ) {
        Optional<UUID> existingAccountId = findStoreManagerAccountId(tenantId, storeId);
        if (existingAccountId.isEmpty() && (username == null || passwordHash == null)) {
            return false;
        }
        UUID accountId = existingAccountId.orElseGet(UUID::randomUUID);
        if (existingAccountId.isPresent()) {
            updateStoreManager(accountId, username, displayName, passwordHash, storeId);
        } else {
            insertStoreManager(accountId, tenantId, username, displayName, passwordHash, storeId);
        }
        ensureRole(accountId);
        STORE_MANAGER_PERMISSIONS.forEach(permission -> ensurePermission(accountId, permission));
        replaceStoreAccess(accountId, tenantId, storeId);
        return true;
    }

    public void archiveStoreManagers(UUID tenantId, UUID storeId) {
        jdbc.update(
            """
            update auth_accounts account
            set status = 'disabled',
                deleted_at = coalesce(account.deleted_at, now()),
                updated_at = now(),
                version = account.version + 1
            where account.tenant_id = ?
              and account.actor_type = 'staff'
              and account.deleted_at is null
              and exists (
                  select 1
                  from auth_account_roles role
                  where role.account_id = account.id
                    and role.role_code = 'store_manager'
                    and role.deleted_at is null
              )
              and exists (
                  select 1
                  from auth_account_store_access access
                  where access.account_id = account.id
                    and access.tenant_id = account.tenant_id
                    and access.store_id = ?
                    and access.deleted_at is null
              )
              and not exists (
                  select 1
                  from auth_account_store_access other_access
                  join stores other_store
                    on other_store.id = other_access.store_id
                   and other_store.tenant_id = other_access.tenant_id
                   and other_store.status = 'active'
                   and other_store.deleted_at is null
                  where other_access.account_id = account.id
                    and other_access.tenant_id = account.tenant_id
                    and other_access.store_id <> ?
                    and other_access.deleted_at is null
              )
            """,
            tenantId,
            storeId,
            storeId
        );
    }

    private Optional<UUID> findStoreManagerAccountId(UUID tenantId, UUID storeId) {
        return jdbc.query(
            """
            select account.id
            from auth_accounts account
            join auth_account_roles role
              on role.account_id = account.id
             and role.role_code = 'store_manager'
             and role.deleted_at is null
            join auth_account_store_access access
              on access.account_id = account.id
             and access.tenant_id = account.tenant_id
             and access.store_id = ?
             and access.deleted_at is null
            where account.tenant_id = ?
              and account.actor_type = 'staff'
              and account.deleted_at is null
            order by account.created_at, account.id
            limit 1
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            storeId,
            tenantId
        ).stream().findFirst();
    }

    private void insertStoreManager(
        UUID accountId,
        UUID tenantId,
        String username,
        String displayName,
        String passwordHash,
        UUID defaultStoreId
    ) {
        jdbc.update(
            """
            insert into auth_accounts (
                id, tenant_id, username, display_name, actor_type, status,
                password_hash, password_algo, default_store_id
            )
            values (?, ?, ?, ?, 'staff', 'active', ?, 'bcrypt-lowercase-v1', ?)
            """,
            accountId,
            tenantId,
            username,
            displayName,
            passwordHash,
            defaultStoreId
        );
    }

    private void updateStoreManager(
        UUID accountId,
        String username,
        String displayName,
        String passwordHash,
        UUID defaultStoreId
    ) {
        if (passwordHash == null) {
            jdbc.update(
                """
                update auth_accounts
                set username = coalesce(?, username),
                    display_name = ?,
                    default_store_id = ?,
                    status = 'active',
                    updated_at = now(),
                    version = version + 1
                where id = ?
                  and actor_type = 'staff'
                  and deleted_at is null
                """,
                username,
                displayName,
                defaultStoreId,
                accountId
            );
            return;
        }

        jdbc.update(
            """
            update auth_accounts
            set username = coalesce(?, username),
                display_name = ?,
                default_store_id = ?,
                status = 'active',
                password_hash = ?,
                password_algo = 'bcrypt-lowercase-v1',
                failed_login_count = 0,
                locked_until_at = null,
                updated_at = now(),
                version = version + 1
            where id = ?
              and actor_type = 'staff'
              and deleted_at is null
            """,
            username,
            displayName,
            defaultStoreId,
            passwordHash,
            accountId
        );
    }

    private void ensureRole(UUID accountId) {
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
            STORE_MANAGER_ROLE,
            accountId,
            STORE_MANAGER_ROLE
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

    private void replaceStoreAccess(UUID accountId, UUID tenantId, UUID storeId) {
        jdbc.update(
            """
            update auth_account_store_access
            set deleted_at = now()
            where account_id = ?
              and deleted_at is null
            """,
            accountId
        );
        ensureStoreAccess(accountId, tenantId, storeId);
    }

    private void ensureStoreAccess(UUID accountId, UUID tenantId, UUID storeId) {
        jdbc.update(
            """
            insert into auth_account_store_access (account_id, tenant_id, store_id)
            select ?, ?, ?
            where not exists (
                select 1
                from auth_account_store_access existing
                where existing.account_id = ?
                  and existing.tenant_id = ?
                  and existing.store_id = ?
                  and existing.deleted_at is null
            )
            """,
            accountId,
            tenantId,
            storeId,
            accountId,
            tenantId,
            storeId
        );
    }
}
