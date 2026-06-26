package com.rpb.reservation.platformbilling.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingPeriodCalculatorTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-31T10:00:00Z");

    private final BillingPeriodCalculator calculator = new BillingPeriodCalculator();

    @Test
    void purchaseStartsNowAndAddsCalendarMonths() {
        BillingPeriodCalculation result = calculator.calculate("purchase", null, new BillingDuration("monthly", 1), NOW);

        assertThat(result.periodStart()).isEqualTo(NOW);
        assertThat(result.periodEnd()).isEqualTo(OffsetDateTime.parse("2026-02-28T10:00:00Z"));
    }

    @Test
    void renewalExtendsFromFutureCurrentEnd() {
        ProductSubscription current = subscription(OffsetDateTime.parse("2026-03-31T23:59:59Z"));

        BillingPeriodCalculation result = calculator.calculate("renew", current, new BillingDuration("yearly", 1), NOW);

        assertThat(result.periodStart()).isEqualTo(OffsetDateTime.parse("2026-03-31T23:59:59Z"));
        assertThat(result.periodEnd()).isEqualTo(OffsetDateTime.parse("2027-03-31T23:59:59Z"));
    }

    @Test
    void expiredRenewalStartsNow() {
        ProductSubscription current = subscription(OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        BillingPeriodCalculation result = calculator.calculate("renew", current, new BillingDuration("monthly", 2), NOW);

        assertThat(result.periodStart()).isEqualTo(NOW);
        assertThat(result.periodEnd()).isEqualTo(OffsetDateTime.parse("2026-03-31T10:00:00Z"));
    }

    private static ProductSubscription subscription(OffsetDateTime periodEnd) {
        return new ProductSubscription(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "reservation_queue",
            "预约排队叫号产线",
            "monthly",
            "active",
            "active",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            periodEnd,
            new BigDecimal("128.00"),
            "SGD",
            null,
            "enabled",
            periodEnd,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            0
        );
    }
}
