package com.rpb.reservation.platformbilling.application;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class BillingPeriodCalculator {
    public BillingPeriodCalculation calculate(
        String operation,
        ProductSubscription current,
        BillingDuration duration,
        OffsetDateTime now
    ) {
        OffsetDateTime start = start(operation, current, now);
        OffsetDateTime end = "monthly".equals(duration.billingCycle())
            ? start.plusMonths(duration.durationCount())
            : start.plusYears(duration.durationCount());
        return new BillingPeriodCalculation(start, end);
    }

    private static OffsetDateTime start(String operation, ProductSubscription current, OffsetDateTime now) {
        if (current == null || "purchase".equals(operation) || "convert_from_legacy".equals(operation)) {
            return now;
        }
        OffsetDateTime currentEnd = current.currentPeriodEnd();
        if (currentEnd != null && currentEnd.isAfter(now)) {
            return currentEnd;
        }
        return now;
    }
}
