package com.rpb.reservation.platform.application;

public record PlatformSocialLinkMutationCommand(
    String displayName,
    String url,
    Integer sortOrder,
    String status
) {
}
