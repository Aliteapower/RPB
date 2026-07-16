package com.rpb.reservation.platformbilling.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TenantProductEntitlementSyncGateway {
    void enableTenantApp(UUID tenantId, String appKey, OffsetDateTime validFrom, OffsetDateTime validUntil, UUID operatorUserId);

    void suspendTenantApp(UUID tenantId, String appKey, UUID operatorUserId);

    void disableTenantApp(UUID tenantId, String appKey, UUID operatorUserId);
}
