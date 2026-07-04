package com.rpb.reservation.customerauth.application;

import java.util.UUID;

public record CustomerOAuthVerificationRequest(
    UUID tenantId,
    UUID storeId,
    String provider,
    String token
) {
}
