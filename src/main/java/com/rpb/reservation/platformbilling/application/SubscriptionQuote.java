package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;

public record SubscriptionQuote(
    int durationCount,
    String durationUnit,
    int storeCount,
    BigDecimal unitAmount,
    BigDecimal storeUnitAmount,
    BigDecimal defaultAmount,
    BigDecimal finalAmount,
    String currency,
    String priceSource
) {
    public String eventPayloadJson() {
        return """
            {
              "durationCount": %d,
              "durationUnit": "%s",
              "storeCount": %d,
              "unitAmount": "%s",
              "storeUnitAmount": "%s",
              "defaultAmount": "%s",
              "finalAmount": "%s",
              "currency": "%s",
              "priceSource": "%s",
              "periodCalculatedBy": "backend"
            }
            """.formatted(
            durationCount,
            json(durationUnit),
            storeCount,
            amount(unitAmount),
            amount(storeUnitAmount),
            amount(defaultAmount),
            amount(finalAmount),
            json(currency),
            json(priceSource)
        );
    }

    private static String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
