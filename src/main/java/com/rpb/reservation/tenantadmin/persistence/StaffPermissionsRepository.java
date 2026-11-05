package com.rpb.reservation.tenantadmin.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import java.sql.Array;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffPermissionsRepository {
    public record Target(String actorType, Set<String> roles, Set<String> permissions) {}
    public record Configuration(long version, Set<String> permissions) {}
    private final JdbcTemplate jdbc;
    public StaffPermissionsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Target> target(StoreScope scope, UUID accountId, boolean lock) {
        var accounts = jdbc.query("""
            select account.actor_type
            from auth_accounts account
            where account.id = ? and account.tenant_id = ? and account.deleted_at is null
              and exists (select 1 from auth_account_store_access access
                  join stores store on store.id = access.store_id and store.tenant_id = access.tenant_id
                  where access.account_id = account.id and access.tenant_id = ? and access.store_id = ?
                    and access.deleted_at is null and store.deleted_at is null and store.status = 'active')
            """ + (lock ? " for update of account" : ""),
            (rs, row) -> rs.getString("actor_type"), accountId, scope.tenantId().value(), scope.tenantId().value(), scope.storeId().value());
        return accounts.stream().findFirst().map(type -> new Target(type, strings("auth_account_roles", "role_code", accountId),
            strings("auth_account_permissions", "permission_code", accountId)));
    }
    public Optional<Configuration> find(StoreScope scope, UUID accountId) {
        return jdbc.query("""
            select version, permissions from auth_staff_store_permissions
            where tenant_id = ? and store_id = ? and account_id = ?
            """, (rs, row) -> new Configuration(rs.getLong("version"), array(rs.getArray("permissions"))),
            scope.tenantId().value(), scope.storeId().value(), accountId).stream().findFirst();
    }
    public void save(StoreScope scope, UUID accountId, Set<String> permissions, long version) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                insert into auth_staff_store_permissions (tenant_id,store_id,account_id,permissions,version)
                values (?,?,?,?,?) on conflict (account_id,store_id) do update
                set permissions=excluded.permissions, version=excluded.version, updated_at=now()
                where auth_staff_store_permissions.tenant_id=excluded.tenant_id
                """);
            statement.setObject(1, scope.tenantId().value()); statement.setObject(2, scope.storeId().value());
            statement.setObject(3, accountId); statement.setArray(4, connection.createArrayOf("text", permissions.stream().sorted().toArray(String[]::new)));
            statement.setLong(5, version); return statement;
        });
    }
    private Set<String> strings(String table, String column, UUID accountId) {
        return new HashSet<>(jdbc.queryForList("select " + column + " from " + table + " where account_id=? and deleted_at is null", String.class, accountId));
    }
    private static Set<String> array(Array value) throws SQLException {
        if (value == null) return Set.of();
        try { return Set.copyOf(Arrays.asList((String[]) value.getArray())); } finally { value.free(); }
    }
}
