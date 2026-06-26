package com.rpb.reservation.platformbilling.api;

import java.math.BigDecimal;
import java.util.List;

final class PlatformProductLinePriceRequests {
    private PlatformProductLinePriceRequests() {
    }

    record UpdatePricesRequest(List<UpdatePriceItem> prices) {
    }

    record UpdatePriceItem(
        String billingCycle,
        BigDecimal amount,
        String currency,
        String status,
        Integer version
    ) {
    }
}
