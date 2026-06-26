package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSubscriptionEventDraft(
    UUID subscriptionId,
    UUID tenantId,
    String appKey,
    String eventType,
    String billingCycle,
    String status,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    BigDecimal amount,
    String currency,
    String paymentNote,
    String idempotencyKey,
    UUID operatorUserId
) {
}
