package com.rpb.reservation.platform.application;

public record PlatformProfileMutationCommand(
    String platformName,
    String uen,
    String address,
    String phone,
    String email,
    String website
) {
}
