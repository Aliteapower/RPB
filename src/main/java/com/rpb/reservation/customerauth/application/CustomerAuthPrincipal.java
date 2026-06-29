package com.rpb.reservation.customerauth.application;

import java.util.UUID;

public record CustomerAuthPrincipal(
    UUID tenantId,
    UUID authAccountId,
    UUID customerId,
    String email,
    String displayName
) {
}
