package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSubscriptionUpdate(
    UUID tenantId,
    UUID subscriptionId,
    String billingCycle,
    String status,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    BigDecimal amount,
    String currency,
    String paymentNote,
    UUID operatorUserId,
    int expectedVersion
) {
}
