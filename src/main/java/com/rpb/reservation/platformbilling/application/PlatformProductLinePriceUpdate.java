package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;

public record PlatformProductLinePriceUpdate(
    String billingCycle,
    BigDecimal amount,
    String currency,
    String status,
    Integer version
) {
}
