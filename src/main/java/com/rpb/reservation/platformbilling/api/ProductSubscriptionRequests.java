package com.rpb.reservation.platformbilling.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

record ProductSubscriptionMutationRequest(
    String idempotencyKey,
    String appKey,
    String billingCycle,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    BigDecimal amount,
    String currency,
    String paymentNote,
    Integer durationCount,
    Integer version
) {
}

record ProductSubscriptionStatusRequest(
    String idempotencyKey,
    String paymentNote,
    Integer version
) {
}
