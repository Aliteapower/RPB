package com.rpb.reservation.customer.api;

import java.util.UUID;

public record CustomerPhoneLookupCustomerResponse(
    UUID customerId,
    String displayName,
    String nickname,
    String phoneE164
) {
}
