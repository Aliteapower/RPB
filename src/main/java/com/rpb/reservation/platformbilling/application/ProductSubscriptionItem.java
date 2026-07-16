package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSubscriptionItem(
    UUID id,
    UUID subscriptionId,
    UUID tenantId,
    String appKey,
    String scopeType,
    UUID storeId,
    String storeCode,
    String storeName,
    UUID operatingEntityId,
    String operatingEntityName,
    String billingCycle,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    int quantity,
    BigDecimal unitAmount,
    BigDecimal amount,
    String currency,
    String status,
    String paymentNote,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int version
) {
}
