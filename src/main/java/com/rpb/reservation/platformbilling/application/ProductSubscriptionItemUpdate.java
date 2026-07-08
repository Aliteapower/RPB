package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSubscriptionItemUpdate(
    UUID tenantId,
    UUID subscriptionId,
    UUID itemId,
    String billingCycle,
    String status,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    BigDecimal unitAmount,
    BigDecimal amount,
    String currency,
    String paymentNote,
    int expectedVersion
) {
}
