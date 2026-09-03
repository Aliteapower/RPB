package com.rpb.reservation.payment.application;

import java.math.BigDecimal;

public record PaymentProfileTestQr(
    String method,
    BigDecimal amount,
    String currency,
    String paymentReference,
    String qrPayload
) {
}
