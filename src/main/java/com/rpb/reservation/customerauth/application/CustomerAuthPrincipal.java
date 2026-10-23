package com.rpb.reservation.customerauth.application;

import java.util.UUID;

public record CustomerAuthPrincipal(
    UUID tenantId,
    UUID authAccountId,
    UUID customerId,
    String email,
    String displayName,
    String phoneE164
) {
    public CustomerAuthPrincipal(UUID tenantId, UUID authAccountId, UUID customerId, String email, String displayName) {
        this(tenantId, authAccountId, customerId, email, displayName, null);
    }
}
