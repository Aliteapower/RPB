package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentProofTemplateSample(
    UUID id,
    UUID templateId,
    String fileName,
    String contentType,
    String fileDigest,
    String ocrReference,
    BigDecimal ocrAmount,
    String rawText,
    OffsetDateTime createdAt
) {
}
