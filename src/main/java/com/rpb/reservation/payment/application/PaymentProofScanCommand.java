package com.rpb.reservation.payment.application;

import java.time.LocalDate;

public record PaymentProofScanCommand(
    String idempotencyKey,
    String originalFileName,
    String contentType,
    byte[] fileBytes,
    LocalDate businessDate,
    String terminalCode
) {
}
