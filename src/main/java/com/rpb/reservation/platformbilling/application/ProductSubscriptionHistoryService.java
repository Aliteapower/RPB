package com.rpb.reservation.platformbilling.application;

import com.rpb.reservation.platformbilling.persistence.ProductSubscriptionHistoryRepository;
import com.rpb.reservation.platformbilling.persistence.ProductSubscriptionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSubscriptionHistoryService {
    private final ProductSubscriptionRepository subscriptions;
    private final ProductSubscriptionHistoryRepository events;
    public ProductSubscriptionHistoryService(ProductSubscriptionRepository subscriptions, ProductSubscriptionHistoryRepository events) {
        this.subscriptions = subscriptions;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ProductSubscriptionHistoryEvent> list(UUID tenantId, UUID subscriptionId) {
        if (subscriptions.findByTenantIdAndId(tenantId, subscriptionId).isEmpty())
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.SUBSCRIPTION_NOT_FOUND);
        return events.list(tenantId, subscriptionId);
    }
}
