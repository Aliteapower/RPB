package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.ProductSubscriptionEventDraft;
import java.util.Optional;
import java.util.UUID;

public interface ProductSubscriptionEventRepository {
    Optional<UUID> findSubscriptionIdByIdempotencyKey(UUID tenantId, String appKey, String eventType, String idempotencyKey);

    void append(ProductSubscriptionEventDraft draft);
}
