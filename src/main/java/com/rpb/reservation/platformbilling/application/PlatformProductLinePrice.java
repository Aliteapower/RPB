package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;

public record PlatformProductLinePrice(
    String appKey,
    String billingCycle,
    BigDecimal amount,
    String currency,
    String status,
    int version
) {
}
