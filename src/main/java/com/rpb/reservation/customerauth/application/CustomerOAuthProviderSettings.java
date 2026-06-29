package com.rpb.reservation.customerauth.application;

public record CustomerOAuthProviderSettings(
    boolean enabled,
    String provider,
    String clientId,
    String clientSecret,
    boolean secretConfigured
) {
    public CustomerOAuthProviderSettings(
        boolean enabled,
        String provider,
        String clientId,
        boolean secretConfigured
    ) {
        this(enabled, provider, clientId, null, secretConfigured);
    }

    public static CustomerOAuthProviderSettings disabled(String provider) {
        return new CustomerOAuthProviderSettings(false, provider, null, null, false);
    }
}
