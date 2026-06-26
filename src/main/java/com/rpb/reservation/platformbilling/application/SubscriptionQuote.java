package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;

public record SubscriptionQuote(
    int durationCount,
    String durationUnit,
    BigDecimal unitAmount,
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
              "unitAmount": "%s",
              "defaultAmount": "%s",
              "finalAmount": "%s",
              "currency": "%s",
              "priceSource": "%s",
              "periodCalculatedBy": "backend"
            }
            """.formatted(
            durationCount,
            json(durationUnit),
            amount(unitAmount),
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
