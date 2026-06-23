package com.rpb.reservation.customer.application;

import java.util.UUID;

public record CustomerPhoneLookupCustomer(
    UUID customerId,
    String displayName,
    String nickname,
    String phoneE164
) {
}
