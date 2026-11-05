package com.rpb.reservation.appgate.persistence.repository;

import com.rpb.reservation.appgate.domain.StoreAppSubscription;
import java.util.Map;
import java.util.UUID;

public interface StoreAppSubscriptionRepository {
    /** Absent keys retain historical tenant-only authorization. Present keys require this store's billing row. */
    Map<String, StoreAppSubscription> findByTenantAndStore(UUID tenantId, UUID storeId);
}
