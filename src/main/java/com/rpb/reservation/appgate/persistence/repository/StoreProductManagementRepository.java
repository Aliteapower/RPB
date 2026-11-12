package com.rpb.reservation.appgate.persistence.repository;

import com.rpb.reservation.common.scope.StoreScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Locks the owning store, including when its product setting has not yet been created. */
@Repository
public class StoreProductManagementRepository {
    private final JdbcTemplate jdbc;
    public StoreProductManagementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean isActive(StoreScope scope, boolean lock) {
        String sql = "select s.id from stores s join tenants t on t.id=s.tenant_id where s.tenant_id=? and s.id=? and s.status='active' and s.deleted_at is null and t.status='active'";
        return !jdbc.queryForList(sql + (lock ? " for update of s" : ""), scope.tenantId().value(), scope.storeId().value()).isEmpty();
    }
}
