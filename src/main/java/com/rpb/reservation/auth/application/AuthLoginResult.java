package com.rpb.reservation.auth.application;

import java.time.Instant;
import java.util.UUID;

public record AuthLoginResult(
    AuthPrincipal principal,
    UUID entryStoreId,
    String sessionToken,
    Instant expiresAt
) {
}
