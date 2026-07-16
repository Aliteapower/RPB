package com.rpb.reservation.customerauth.application;

public record CustomerEmailSettingsCommand(
    Boolean enabled,
    String fromEmail,
    String fromName,
    String smtpHost,
    Integer smtpPort,
    String smtpUsername,
    String smtpPassword,
    Boolean smtpStartTls
) {
}
