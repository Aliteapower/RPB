package com.rpb.reservation.payment.api;

public record PaymentProfileRequest(
    String method,
    String status,
    String paynowType,
    String paynowMobile,
    String paynowUen,
    String merchantName,
    String currency,
    String configJson,
    Integer version
) {
}
