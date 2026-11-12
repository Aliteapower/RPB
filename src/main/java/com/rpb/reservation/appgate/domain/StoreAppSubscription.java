package com.rpb.reservation.appgate.domain;

import java.time.OffsetDateTime;

/** Read-only commercial boundary for a product that already has store billing rows. */
public record StoreAppSubscription(String subscriptionStatus, String storeStatus, OffsetDateTime validUntil) {
    public AppGateDenyReason denialAt(OffsetDateTime now) {
        if ("expired".equals(storeStatus) || "expired".equals(subscriptionStatus)) {
            return AppGateDenyReason.STORE_APP_EXPIRED;
        }
        if (!"active".equals(subscriptionStatus) || !"active".equals(storeStatus)) {
            return AppGateDenyReason.STORE_APP_NOT_ENABLED;
        }
        return validUntil != null && !validUntil.isAfter(now) ? AppGateDenyReason.STORE_APP_EXPIRED : null;
    }
}
