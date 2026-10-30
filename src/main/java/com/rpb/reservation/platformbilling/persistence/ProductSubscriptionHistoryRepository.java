package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.ProductSubscriptionHistoryEvent;
import java.util.List;
import java.util.UUID;

public interface ProductSubscriptionHistoryRepository {
    List<ProductSubscriptionHistoryEvent> list(UUID tenantId, UUID subscriptionId);
}
