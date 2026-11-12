package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentIntentCreateCommand(
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
