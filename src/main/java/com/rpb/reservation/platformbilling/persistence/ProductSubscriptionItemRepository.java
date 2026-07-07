package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.BillableStore;
import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionItem;
import com.rpb.reservation.platformbilling.application.SubscriptionQuote;
import java.util.List;
import java.util.UUID;

public interface ProductSubscriptionItemRepository {
    List<BillableStore> listActiveStores(UUID tenantId);

    List<ProductSubscriptionItem> listByTenantId(UUID tenantId);

    void replaceStoreItems(ProductSubscription subscription, List<BillableStore> billableStores, SubscriptionQuote quote);

    void updateStatus(UUID tenantId, UUID subscriptionId, String status);
}
