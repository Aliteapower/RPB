package com.rpb.reservation.platformbilling.application;

public record PlatformProductLineMutationCommand(
    String displayName,
    String status,
    String description,
    Integer sortOrder
) {
}
