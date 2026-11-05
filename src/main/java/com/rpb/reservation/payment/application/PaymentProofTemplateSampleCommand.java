package com.rpb.reservation.payment.application;

import java.math.BigDecimal;

public record PaymentProofTemplateSampleCommand(
    String fileName,
    String contentType,
    String fileDigest,
    String ocrReference,
    BigDecimal ocrAmount,
    String rawText
) {
}
