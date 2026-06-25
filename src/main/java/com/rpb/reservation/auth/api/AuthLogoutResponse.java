package com.rpb.reservation.auth.api;

public record AuthLogoutResponse(
    boolean success
) {
    public static AuthLogoutResponse ok() {
        return new AuthLogoutResponse(true);
    }
}
