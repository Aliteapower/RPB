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
    private static final String PROVIDER_SMTP = "smtp";

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
        return new CustomerEmailSettings(false, PROVIDER_SMTP, null, null, null, 587, null, null, true, false);
    }

    public boolean usableForLoginCode() {
        return enabled
            && PROVIDER_SMTP.equals(provider)
            && hasText(fromEmail)
            && hasText(smtpHost)
            && smtpPort > 0
            && (!hasText(smtpUsername) || secretConfigured || hasText(smtpPassword));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
