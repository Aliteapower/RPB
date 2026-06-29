package com.rpb.reservation.customerauth.application;

public record CustomerEmailSettings(
    boolean enabled,
    String provider,
    String fromEmail,
    String fromName,
    String smtpHost,
    int smtpPort,
    String smtpUsername,
    String smtpPassword,
    boolean smtpStartTls,
    boolean secretConfigured
) {
    public CustomerEmailSettings(
        boolean enabled,
        String provider,
        String fromEmail,
        String fromName,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        boolean secretConfigured
    ) {
        this(enabled, provider, fromEmail, fromName, smtpHost, smtpPort, smtpUsername, null, true, secretConfigured);
    }

    public static CustomerEmailSettings disabled() {
        return new CustomerEmailSettings(false, "smtp", null, null, null, 587, null, null, true, false);
    }
}
