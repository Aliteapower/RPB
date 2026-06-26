package com.rpb.reservation.platformbilling.application;

import java.time.OffsetDateTime;

public record BillingPeriodCalculation(
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd
) {
}
