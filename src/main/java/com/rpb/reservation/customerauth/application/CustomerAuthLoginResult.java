package com.rpb.reservation.customerauth.application;

import java.time.Instant;

public record CustomerAuthLoginResult(
    CustomerAuthPrincipal principal,
    String sessionToken,
    Instant expiresAt
) {
}
