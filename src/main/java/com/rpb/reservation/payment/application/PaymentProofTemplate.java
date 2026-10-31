package com.rpb.reservation.payment.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentProofTemplate(
    UUID id,
    UUID tenantId,
    String bankCode,
    String bankName,
    String locale,
    String templateName,
    String source,
    String status,
    int priority,
    int version,
    String layoutJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public boolean active() {
        return "active".equals(status);
    }

    public boolean tenantOwned() {
        return tenantId != null;
    }
}
