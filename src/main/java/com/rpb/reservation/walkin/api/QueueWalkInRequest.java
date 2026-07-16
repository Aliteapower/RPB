package com.rpb.reservation.walkin.api;

import java.util.UUID;

public record QueueWalkInRequest(
    Integer partySize,
    UUID customerId,
    String customerName,
    String customerNickname,
    String phoneE164,
    String note
) {
}
