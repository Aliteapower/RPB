package com.rpb.reservation.customerauth.application;

public record CustomerOAuthProviderSettingsCommand(
    String provider,
    Boolean enabled,
    String clientId,
    String clientSecret
) {
}
