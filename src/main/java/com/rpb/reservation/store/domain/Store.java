package com.rpb.reservation.store.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.Objects;

/**
 * Store domain skeleton. Store owns operational timezone, locale, and physical
 * operation scope but not cross-Tenant identity.
 */
public record Store(
    StoreId id,
    TenantId tenantId,
    String storeCode,
    String timezone,
    String locale,
    String status
) {

    public Store {
        Objects.requireNonNull(id, "store_id_required");
        Objects.requireNonNull(tenantId, "tenant_id_required");
        requireText(storeCode, "store_code_required");
        requireText(timezone, "store_timezone_required");
        requireText(locale, "store_locale_required");
        requireText(status, "store_status_required");
    }

    public static Store skeleton(StoreId id, TenantId tenantId, String storeCode, String timezone, String locale, String status) {
        return new Store(id, tenantId, storeCode, timezone, locale, status);
    }

    public StoreScope scope() {
        return new StoreScope(tenantId, id);
    }

    public String updateLocaleIntent() {
        return "store.update_locale.intent";
    }

    public String domainBoundary() {
        return "Store owns operation scope but not Tenant-wide Customer uniqueness.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
