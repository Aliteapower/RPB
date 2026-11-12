package com.rpb.reservation.payment.application;

public record PaymentMethodProfileCommand(
    String method,
    String status,
    String paynowType,
    String paynowMobile,
    String paynowUen,
    String merchantName,
    String currency,
    String configJson,
    int version
) {
}
