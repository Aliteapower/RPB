package com.rpb.reservation.auth.api;

public record LoginRequest(
    String username,
    String password,
    String captchaId,
    Integer captchaX
) {
}
