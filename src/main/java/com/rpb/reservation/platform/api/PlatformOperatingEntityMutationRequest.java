package com.rpb.reservation.platform.api;

public record PlatformOperatingEntityMutationRequest(
    String entityCode,
    String displayName,
    String status,
    String defaultLocale,
    String contactPhone,
    String address,
    String principalName
) {
}
