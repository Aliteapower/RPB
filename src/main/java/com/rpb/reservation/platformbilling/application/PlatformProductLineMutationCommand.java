package com.rpb.reservation.platformbilling.application;

public record PlatformProductLineMutationCommand(
    String displayName,
    String status,
    String defaultEntryRoute,
    String description,
    Integer sortOrder
) {
    public PlatformProductLineMutationCommand(String displayName, String status, String description, Integer sortOrder) {
        this(displayName, status, null, description, sortOrder);
    }
}
