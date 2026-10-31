package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Immutable read model; all store values come from the original event payload. */
public record ProductSubscriptionHistoryEvent(
    UUID id, String eventType, String billingCycle, String status,
    OffsetDateTime periodStart, OffsetDateTime periodEnd, BigDecimal amount,
    String currency, String paymentNote, OffsetDateTime createdAt,
    boolean snapshotAvailable, List<StoreSnapshot> stores
) {
    public ProductSubscriptionHistoryEvent { stores = List.copyOf(stores); }

    public record StoreSnapshot(
        UUID storeId, String storeCode, String storeName, UUID operatingEntityId,
        String operatingEntityName, String storeStatus, String itemStatus,
        Integer quantity, BigDecimal unitAmount, BigDecimal amount, String currency,
        OffsetDateTime periodStart, OffsetDateTime periodEnd
    ) {}
}
