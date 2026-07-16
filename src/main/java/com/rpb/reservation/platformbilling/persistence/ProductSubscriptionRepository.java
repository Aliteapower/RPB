package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionDraft;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionUpdate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSubscriptionRepository {
    boolean existsActiveTenant(UUID tenantId);

    Optional<PlatformProductLine> findProductLine(String appKey);

    List<ProductSubscription> listByTenantId(UUID tenantId);

    Optional<ProductSubscription> findByTenantIdAndId(UUID tenantId, UUID subscriptionId);

    Optional<ProductSubscription> findByTenantIdAndAppKey(UUID tenantId, String appKey);

    ProductSubscription create(ProductSubscriptionDraft draft);

    ProductSubscription update(ProductSubscriptionUpdate update);
}
