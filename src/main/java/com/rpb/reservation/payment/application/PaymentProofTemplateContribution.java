package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentProofTemplateContribution(
    UUID id,
    UUID tenantId,
    UUID storeId,
    UUID sourceTemplateId,
    UUID platformTemplateId,
    String bankCode,
    String bankName,
    String locale,
    String templateName,
    String layoutJson,
    String sampleFileName,
    String sampleContentType,
    String sampleFileDigest,
    String sampleRawText,
    String sampleOcrReference,
    BigDecimal sampleOcrAmount,
    String status,
    String reviewNote,
    UUID submittedBy,
    UUID reviewedBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime reviewedAt,
    int version
) {
}
