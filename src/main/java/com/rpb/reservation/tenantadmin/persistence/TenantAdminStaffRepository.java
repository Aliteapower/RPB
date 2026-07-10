package com.rpb.reservation.tenantadmin.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantAdminSearchCriteria;
import com.rpb.reservation.tenantadmin.application.TenantAdminStaff;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantAdminStaffRepository {
    private static final String STORE_STAFF_ROLE = "store_staff";
    private static final String STAFF_ACCOUNT_TYPE = "staff";
    private static final String TENANT_ADMIN = "tenant_admin";
    private static final List<String> STAFF_PERMISSIONS = List.of(
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

    public TenantAdminStaffRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TenantAdminStaff> list(StoreScope scope, TenantAdminSearchCriteria criteria) {
        WhereClause where = whereClause(scope, criteria.keyword());
        List<Object> args = new ArrayList<>(where.args());
        args.add(criteria.limit());
        args.add(criteria.offset());
        return jdbc.query(
            """
            select account.id, account.username, account.display_name, account.status, account.default_store_id,
                   account.contact_phone, account.email, account.created_at, account.updated_at
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            %s
            order by account.username
            limit ? offset ?
            """.formatted(where.sql()),
            this::staff,
            args.toArray()
        );
    }

    public int count(StoreScope scope, TenantAdminSearchCriteria criteria) {
        WhereClause where = whereClause(scope, criteria.keyword());
        Integer total = jdbc.queryForObject(
            """
            select count(*)
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            %s
            """.formatted(where.sql()),
            Integer.class,
            where.args().toArray()
        );
        return total == null ? 0 : total;
    }

    public Optional<TenantAdminStaff> findById(StoreScope scope, UUID staffId) {
        return jdbc.query(
            """
            select account.id, account.username, account.display_name, account.status, account.default_store_id,
                   account.contact_phone, account.email, account.created_at, account.updated_at
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            where account.id = ?
              and account.tenant_id = ?
              and account.actor_type = 'staff'
              and account.deleted_at is null
              and access.tenant_id = ?
              and access.store_id = ?
              and access.deleted_at is null
            """,
            this::staff,
            staffId,
            scope.tenantId().value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    private Optional<TenantAdminStaff> findByIdInTenant(StoreScope scope, UUID staffId) {
        return jdbc.query(
            """
            select account.id, account.username, account.display_name, account.status, account.default_store_id,
                   account.contact_phone, account.email, account.created_at, account.updated_at
            from auth_accounts account
            where account.id = ?
              and account.tenant_id = ?
              and account.actor_type = 'staff'
              and account.deleted_at is null
            """,
            this::staff,
            staffId,
            scope.tenantId().value()
        ).stream().findFirst();
    }

    public Optional<TenantAdminStaff> findTenantAdminById(StoreScope scope, UUID accountId) {
        return jdbc.query(
            """
            select account.id, account.username, account.display_name, account.status, account.default_store_id,
                   account.contact_phone, account.email, account.created_at, account.updated_at
            from auth_accounts account
            join auth_account_store_access access on access.account_id = account.id
            join stores store
              on store.id = access.store_id
             and store.tenant_id = access.tenant_id
             and store.status = 'active'
             and store.deleted_at is null
            where account.id = ?
              and account.tenant_id = ?
              and account.actor_type <> 'platform_admin'
              and account.deleted_at is null
              and exists (
                  select 1
                  from auth_account_roles role
                  where role.account_id = account.id
                    and role.role_code = 'tenant_admin'
                    and role.deleted_at is null
              )
              and exists (
                  select 1
                  from auth_account_permissions permission
                  where permission.account_id = account.id
                    and permission.permission_code = 'tenant.admin.manage'
                    and permission.deleted_at is null
              )
              and access.tenant_id = ?
              and access.store_id = ?
              and access.deleted_at is null
            """,
            this::tenantAdminSelf,
            accountId,
            scope.tenantId().value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    public Set<UUID> activeStoreIds(UUID tenantId, List<UUID> storeIds) {
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

    public TenantAdminStaff insert(
        StoreScope scope,
        UUID staffId,
        String employeeNo,
        String name,
        String phone,
        String email,
        String passwordHash,
        UUID defaultStoreId,
        List<UUID> storeIds
    ) {
        jdbc.update(
            """
            insert into auth_accounts (
                id, tenant_id, username, display_name, actor_type, status,
                password_hash, password_algo, default_store_id, contact_phone, email
            )
            values (?, ?, ?, ?, 'staff', 'active', ?, 'bcrypt-lowercase-v1', ?, ?, ?)
            """,
            staffId,
            scope.tenantId().value(),
            employeeNo,
            name,
            passwordHash,
            defaultStoreId,
            phone,
            email
        );
        ensureRole(staffId, STORE_STAFF_ROLE);
        STAFF_PERMISSIONS.forEach(permission -> ensurePermission(staffId, permission));
        storeIds.forEach(storeId -> ensureStoreAccess(staffId, scope.tenantId().value(), storeId));
        return findByIdInTenant(scope, staffId).orElseThrow();
    }

    public Optional<TenantAdminStaff> update(
        StoreScope scope,
        UUID staffId,
        String name,
        String phone,
        String email,
        String status,
        String passwordHash,
        UUID defaultStoreId,
        List<UUID> storeIds,
        boolean replaceStoreAccess
    ) {
        if (passwordHash == null) {
            jdbc.update(
                """
                update auth_accounts
                set display_name = ?,
                    contact_phone = ?,
                    email = ?,
                    status = ?,
                    default_store_id = ?,
                    updated_at = now(),
                    version = version + 1
                where id = ?
                  and tenant_id = ?
                  and actor_type = 'staff'
                  and deleted_at is null
                """,
                name,
                phone,
                email,
                status,
                defaultStoreId,
                staffId,
                scope.tenantId().value()
            );
            if (replaceStoreAccess) {
                replaceStoreAccess(staffId, scope.tenantId().value(), storeIds);
            }
            return findByIdInTenant(scope, staffId);
        }

        jdbc.update(
            """
            update auth_accounts
            set display_name = ?,
                contact_phone = ?,
                email = ?,
                status = ?,
                default_store_id = ?,
                password_hash = ?,
                password_algo = 'bcrypt-lowercase-v1',
                failed_login_count = 0,
                locked_until_at = null,
                updated_at = now(),
                version = version + 1
            where id = ?
              and tenant_id = ?
              and actor_type = 'staff'
              and deleted_at is null
            """,
            name,
            phone,
            email,
            status,
            defaultStoreId,
            passwordHash,
            staffId,
            scope.tenantId().value()
        );
        if (replaceStoreAccess) {
            replaceStoreAccess(staffId, scope.tenantId().value(), storeIds);
        }
        return findByIdInTenant(scope, staffId);
    }

    public Optional<TenantAdminStaff> updateTenantAdminSelf(
        StoreScope scope,
        UUID accountId,
        String name,
        String phone,
        String email,
        String passwordHash
    ) {
        if (passwordHash == null) {
            jdbc.update(
                """
                update auth_accounts
                set display_name = ?,
                    contact_phone = ?,
                    email = ?,
                    updated_at = now(),
                    version = version + 1
                where id = ?
                  and tenant_id = ?
                  and actor_type <> 'platform_admin'
                  and deleted_at is null
                  and exists (
                      select 1
                      from auth_account_roles role
                      where role.account_id = auth_accounts.id
                        and role.role_code = 'tenant_admin'
                        and role.deleted_at is null
                  )
                  and exists (
                      select 1
                      from auth_account_permissions permission
                      where permission.account_id = auth_accounts.id
                        and permission.permission_code = 'tenant.admin.manage'
                        and permission.deleted_at is null
                  )
                  and exists (
                      select 1
                      from auth_account_store_access access
                      join stores store
                        on store.id = access.store_id
                       and store.tenant_id = access.tenant_id
                       and store.status = 'active'
                       and store.deleted_at is null
                      where access.account_id = auth_accounts.id
                        and access.tenant_id = ?
                        and access.store_id = ?
                        and access.deleted_at is null
                  )
                """,
                name,
                phone,
                email,
                accountId,
                scope.tenantId().value(),
                scope.tenantId().value(),
                scope.storeId().value()
            );
            return findTenantAdminById(scope, accountId);
        }

        jdbc.update(
            """
            update auth_accounts
            set display_name = ?,
                contact_phone = ?,
                email = ?,
                password_hash = ?,
                password_algo = 'bcrypt-lowercase-v1',
                failed_login_count = 0,
                locked_until_at = null,
                updated_at = now(),
                version = version + 1
            where id = ?
              and tenant_id = ?
              and actor_type <> 'platform_admin'
              and deleted_at is null
              and exists (
                  select 1
                  from auth_account_roles role
                  where role.account_id = auth_accounts.id
                    and role.role_code = 'tenant_admin'
                    and role.deleted_at is null
              )
              and exists (
                  select 1
                  from auth_account_permissions permission
                  where permission.account_id = auth_accounts.id
                    and permission.permission_code = 'tenant.admin.manage'
                    and permission.deleted_at is null
              )
              and exists (
                  select 1
                  from auth_account_store_access access
                  join stores store
                    on store.id = access.store_id
                   and store.tenant_id = access.tenant_id
                   and store.status = 'active'
                   and store.deleted_at is null
                  where access.account_id = auth_accounts.id
                    and access.tenant_id = ?
                    and access.store_id = ?
                    and access.deleted_at is null
              )
            """,
            name,
            phone,
            email,
            passwordHash,
            accountId,
            scope.tenantId().value(),
            scope.tenantId().value(),
            scope.storeId().value()
        );
        return findTenantAdminById(scope, accountId);
    }

    private void ensureRole(UUID accountId, String roleCode) {
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
            roleCode,
            accountId,
            roleCode
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

    private void replaceStoreAccess(UUID accountId, UUID tenantId, List<UUID> storeIds) {
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

    private List<UUID> storeIds(UUID accountId) {
        return jdbc.queryForList(
            """
            select access.store_id
            from auth_account_store_access access
            join stores store
              on store.id = access.store_id
             and store.tenant_id = access.tenant_id
             and store.deleted_at is null
             and store.status = 'active'
            where access.account_id = ?
              and access.deleted_at is null
            order by access.store_id
            """,
            UUID.class,
            accountId
        );
    }

    private static WhereClause whereClause(StoreScope scope, String keyword) {
        StringBuilder sql = new StringBuilder("""
            where account.tenant_id = ?
              and account.actor_type = 'staff'
              and account.deleted_at is null
              and access.tenant_id = ?
              and access.store_id = ?
              and access.deleted_at is null
            """);
        List<Object> args = new ArrayList<>();
        args.add(scope.tenantId().value());
        args.add(scope.tenantId().value());
        args.add(scope.storeId().value());
        if (keyword != null) {
            String pattern = "%" + keyword + "%";
            sql.append("""
                  and (
                    lower(account.username) like ?
                    or lower(account.display_name) like ?
                    or lower(coalesce(account.contact_phone, '')) like ?
                    or lower(coalesce(account.email, '')) like ?
                  )
                """);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        return new WhereClause(sql.toString(), args);
    }

    private TenantAdminStaff staff(ResultSet rs, int rowNum) throws SQLException {
        return new TenantAdminStaff(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("contact_phone"),
            rs.getString("email"),
            rs.getString("status"),
            rs.getObject("default_store_id", UUID.class),
            storeIds(rs.getObject("id", UUID.class)),
            STAFF_ACCOUNT_TYPE,
            false,
            true,
            true,
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private TenantAdminStaff tenantAdminSelf(ResultSet rs, int rowNum) throws SQLException {
        return new TenantAdminStaff(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("contact_phone"),
            rs.getString("email"),
            rs.getString("status"),
            rs.getObject("default_store_id", UUID.class),
            storeIds(rs.getObject("id", UUID.class)),
            TENANT_ADMIN,
            true,
            true,
            false,
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private record WhereClause(String sql, List<Object> args) {
    }
}
