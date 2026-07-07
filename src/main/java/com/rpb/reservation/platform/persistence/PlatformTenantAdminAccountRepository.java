package com.rpb.reservation.platform.persistence;

import com.rpb.reservation.platform.application.PlatformTenantAdminStoreAccess;
import com.rpb.reservation.platform.application.PlatformTenantStoreOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        String passwordHash,
        UUID defaultStoreId
    ) {
        Optional<UUID> existingAccountId = findTenantAdminAccountId(tenantId);
        UUID accountId = existingAccountId.orElseGet(UUID::randomUUID);
        if (existingAccountId.isPresent()) {
            updateTenantAdminAccount(accountId, username, displayName, passwordHash, defaultStoreId);
        } else {
            insertTenantAdminAccount(accountId, tenantId, username, displayName, passwordHash, defaultStoreId);
        }
        ensureTenantAdminRole(accountId);
        ensurePermission(accountId, TENANT_ADMIN_MANAGE);
        if (defaultStoreId != null) {
            ensureStoreAccess(accountId, tenantId, defaultStoreId);
        }
    }

    public PlatformTenantAdminStoreAccess tenantAdminStoreAccess(UUID tenantId) {
        Optional<UUID> accountId = findTenantAdminAccountId(tenantId);
        UUID defaultStoreId = accountId.flatMap(this::defaultStoreId).orElse(null);
        return new PlatformTenantAdminStoreAccess(
            activeTenantStores(tenantId, defaultStoreId),
            accountId.map(id -> storeIds(tenantId, id)).orElse(List.of()),
            defaultStoreId
        );
    }

    public Set<UUID> activeTenantStoreIds(UUID tenantId, List<UUID> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(", ", storeIds.stream().map(storeId -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(storeIds);
        return new LinkedHashSet<>(jdbc.queryForList(
            """
            select id
            from stores
            where tenant_id = ?
              and id in (%s)
              and status = 'active'
              and deleted_at is null
            """.formatted(placeholders),
            UUID.class,
            args.toArray()
        ));
    }

    public boolean replaceTenantAdminStoreAccess(UUID tenantId, List<UUID> storeIds, UUID defaultStoreId) {
        Optional<UUID> existingAccountId = findTenantAdminAccountId(tenantId);
        if (existingAccountId.isEmpty()) {
            return false;
        }
        UUID accountId = existingAccountId.get();
        jdbc.update(
            """
            update auth_accounts
            set default_store_id = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and tenant_id = ?
              and actor_type = 'tenant_admin'
              and deleted_at is null
            """,
            defaultStoreId,
            accountId,
            tenantId
        );
        jdbc.update(
            """
            update auth_account_store_access
            set deleted_at = now()
            where account_id = ?
              and tenant_id = ?
              and deleted_at is null
            """,
            accountId,
            tenantId
        );
        storeIds.forEach(storeId -> ensureStoreAccess(accountId, tenantId, storeId));
        return true;
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

    private List<PlatformTenantStoreOption> activeTenantStores(UUID tenantId, UUID defaultStoreId) {
        return jdbc.query(
            """
            select
                store.id,
                store.operating_entity_id,
                entity.display_name as operating_entity_name,
                store.store_code,
                store.display_name,
                store.status,
                store.locale
            from stores store
            left join operating_entities entity
              on entity.id = store.operating_entity_id
             and entity.tenant_id = store.tenant_id
             and entity.deleted_at is null
            where store.tenant_id = ?
              and store.status = 'active'
              and store.deleted_at is null
            order by store.store_code, store.created_at, store.id
            """,
            (rs, rowNum) -> new PlatformTenantStoreOption(
                rs.getObject("id", UUID.class),
                rs.getObject("operating_entity_id", UUID.class),
                rs.getString("operating_entity_name"),
                rs.getString("store_code"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("locale"),
                defaultStoreId != null && defaultStoreId.equals(rs.getObject("id", UUID.class))
            ),
            tenantId
        );
    }

    private Optional<UUID> defaultStoreId(UUID accountId) {
        return jdbc.query(
            """
            select default_store_id
            from auth_accounts
            where id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> rs.getObject("default_store_id", UUID.class),
            accountId
        ).stream().filter(Objects::nonNull).findFirst();
    }

    private List<UUID> storeIds(UUID tenantId, UUID accountId) {
        return jdbc.queryForList(
            """
            select access.store_id
            from auth_account_store_access access
            join stores store
              on store.id = access.store_id
             and store.tenant_id = access.tenant_id
             and store.status = 'active'
             and store.deleted_at is null
            where access.account_id = ?
              and access.tenant_id = ?
              and access.deleted_at is null
            order by store.store_code, access.store_id
            """,
            UUID.class,
            accountId,
            tenantId
        );
    }

    private void insertTenantAdminAccount(
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
                id, tenant_id, username, display_name, actor_type,
                status, password_hash, password_algo, default_store_id
            )
            values (?, ?, ?, ?, 'tenant_admin', 'active', ?, 'bcrypt-lowercase-v1', ?)
            """,
            accountId,
            tenantId,
            username,
            displayName,
            passwordHash,
            defaultStoreId
        );
    }

    private void updateTenantAdminAccount(
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
                set username = ?,
                    display_name = ?,
                    default_store_id = coalesce(?, default_store_id),
                    status = 'active',
                    updated_at = now(),
                    version = version + 1
                where id = ?
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
            set username = ?,
                display_name = ?,
                default_store_id = coalesce(?, default_store_id),
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
            defaultStoreId,
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
