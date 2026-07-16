package com.rpb.reservation.customer.application;

public record CustomerManagementCommand(
    String displayName,
    String nickname,
    String phoneE164,
    String email
) {
}
