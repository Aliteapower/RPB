package com.rpb.reservation.auth.api;

import java.time.Instant;

public record AuthLoginResponse(
    boolean success,
    AuthUserResponse user,
    Instant expiresAt
) {
}
