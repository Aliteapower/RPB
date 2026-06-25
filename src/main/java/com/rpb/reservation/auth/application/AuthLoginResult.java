package com.rpb.reservation.auth.application;

import java.time.Instant;

public record AuthLoginResult(
    AuthPrincipal principal,
    String sessionToken,
    Instant expiresAt
) {
}
