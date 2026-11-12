package com.rpb.reservation.payment.application;

import java.math.BigDecimal;

public record PaymentProofOcrExpected(
    String expectedReference,
    BigDecimal expectedAmount
) {
}
