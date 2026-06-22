package com.rpb.reservation.queue.application;

import java.time.Instant;
import java.util.UUID;

public record QueueTicketListItem(
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    int partySize,
    String partySizeGroup,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    String customerName,
    String customerPhoneMasked,
    Instant createdAt,
    Instant calledAt,
    Instant holdUntilAt,
    Instant expiresAt
) {
}
