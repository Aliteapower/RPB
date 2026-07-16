package com.rpb.reservation.auth.api;

public record AuthMeResponse(
    boolean success,
    AuthUserResponse user
) {
}
