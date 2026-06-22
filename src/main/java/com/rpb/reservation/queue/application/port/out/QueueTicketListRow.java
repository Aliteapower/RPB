package com.rpb.reservation.queue.application.port.out;

import java.time.Instant;
import java.util.UUID;

public record QueueTicketListRow(
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    int partySize,
    String partySizeGroup,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    String customerName,
    String customerPhoneE164,
    Instant createdAt,
    Instant calledAt,
    Instant expiresAt
) {
}
