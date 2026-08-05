package com.rpb.reservation.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

public final class PaymentIntentRequests {
    private PaymentIntentRequests() {
    }

    public record CreateIntentRequest(
        String idempotencyKey,
        String sourceType,
        UUID sourceId,
        String method,
        BigDecimal amount,
        String currency,
        String terminalCode,
        String cashierName,
        Integer requestedDisplayNumber,
        String metadataJson
    ) {
    }
}
