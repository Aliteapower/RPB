package com.rpb.reservation.tenantadmin.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantEntryLinksContext.Identity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantEntryLinksRepository {
    private final JdbcTemplate jdbc;
    public TenantEntryLinksRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Identity> find(StoreScope scope) {
        return jdbc.query("""
            select t.id tenant_id,t.tenant_code,t.display_name tenant_name,t.status tenant_status,
                   s.id store_id,s.store_code,s.display_name store_name,s.status store_status
            from tenants t join stores s on s.tenant_id=t.id
            where t.id=? and s.id=? and t.deleted_at is null and s.deleted_at is null
            """,(rs,n)->new Identity(rs.getObject("tenant_id",UUID.class),rs.getString("tenant_code"),rs.getString("tenant_name"),rs.getString("tenant_status"),rs.getObject("store_id",UUID.class),rs.getString("store_code"),rs.getString("store_name"),rs.getString("store_status")),scope.tenantId().value(),scope.storeId().value()).stream().findFirst();
    }
}
