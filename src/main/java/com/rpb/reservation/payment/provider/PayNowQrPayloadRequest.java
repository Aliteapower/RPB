package com.rpb.reservation.payment.provider;

import java.math.BigDecimal;

public record PayNowQrPayloadRequest(
    String paynowType,
    String paynowMobile,
    String paynowUen,
    String merchantName,
    BigDecimal amount,
    String reference
) {
}
