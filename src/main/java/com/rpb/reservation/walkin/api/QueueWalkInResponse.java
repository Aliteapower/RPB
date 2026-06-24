package com.rpb.reservation.walkin.api;

import java.util.List;
import java.util.UUID;

public record QueueWalkInResponse(
    boolean success,
    UUID walkInId,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    int partySize,
    String partySizeGroup,
    String businessDate,
    Integer queuePosition,
    boolean alreadyQueued,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {
}
