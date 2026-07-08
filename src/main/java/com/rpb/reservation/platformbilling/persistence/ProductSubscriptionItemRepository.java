package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.BillableStore;
import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionItem;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionItemUpdate;
import com.rpb.reservation.platformbilling.application.SubscriptionQuote;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSubscriptionItemRepository {
    List<BillableStore> listActiveStores(UUID tenantId);

    List<ProductSubscriptionItem> listByTenantId(UUID tenantId);

    Optional<ProductSubscriptionItem> findByTenantSubscriptionAndId(UUID tenantId, UUID subscriptionId, UUID itemId);

    void replaceStoreItems(ProductSubscription subscription, List<BillableStore> billableStores, SubscriptionQuote quote);

    ProductSubscriptionItem updateStoreItem(ProductSubscriptionItemUpdate update);

    void updateStatus(UUID tenantId, UUID subscriptionId, String status);
}
