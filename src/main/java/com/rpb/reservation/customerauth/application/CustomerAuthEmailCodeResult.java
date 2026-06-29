package com.rpb.reservation.customerauth.application;

import java.time.Instant;

public record CustomerAuthEmailCodeResult(
    boolean success,
    String email,
    Instant expiresAt,
    String devCode
) {
}
