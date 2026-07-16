package com.rpb.reservation.auth.application;

import java.time.Instant;

public record AuthSessionAuthentication(
    AuthPrincipal principal,
    Instant expiresAt
) {
}
