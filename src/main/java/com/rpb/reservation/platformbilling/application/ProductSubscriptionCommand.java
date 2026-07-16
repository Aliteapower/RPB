package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductSubscriptionCommand(
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
    public ProductSubscriptionCommand(
        String idempotencyKey,
        String appKey,
        String billingCycle,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        BigDecimal amount,
        String currency,
        String paymentNote,
        Integer version
    ) {
        this(
            idempotencyKey,
            appKey,
            billingCycle,
            currentPeriodStart,
            currentPeriodEnd,
            amount,
            currency,
            paymentNote,
            null,
            version
        );
    }
}
