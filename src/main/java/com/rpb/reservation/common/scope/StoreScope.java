package com.rpb.reservation.common.scope;

import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * Store operation scope value boundary. It carries Tenant + Store identity only
 * and performs no authorization, database lookup, or UI work.
 */
public record StoreScope(TenantId tenantId, StoreId storeId) {

    public StoreScope {
        Objects.requireNonNull(tenantId, "tenant_scope_missing");
        Objects.requireNonNull(storeId, "store_scope_missing");
    }

    public StoreScope(TenantId tenantId, UUID storeId) {
        this(tenantId, new StoreId(storeId));
    }

    public TenantScope tenantScope() {
        return new TenantScope(tenantId);
    }
}
