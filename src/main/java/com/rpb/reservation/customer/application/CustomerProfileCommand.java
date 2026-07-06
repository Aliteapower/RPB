package com.rpb.reservation.customer.application;

import java.util.UUID;

public record CustomerProfileCommand(
    UUID customerId,
    String displayName,
    String nickname,
    String phoneE164,
    String email
) {
}
