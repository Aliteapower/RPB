package com.rpb.reservation.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentProofTemplateContributionCommand(
    UUID sourceTemplateId,
    String bankCode,
    String bankName,
    String locale,
    String templateName,
    String layoutJson,
    String sampleFileName,
    String sampleContentType,
    String sampleFileDigest,
    String sampleOcrReference,
    BigDecimal sampleOcrAmount,
    String sampleRawText
) {
}
