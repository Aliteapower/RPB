package com.rpb.reservation.platform.application;

public record PlatformOperatingEntityMutationCommand(
    String entityCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName
) {
}
