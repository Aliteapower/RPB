package com.rpb.reservation.queue.api;

import java.time.Instant;

public record CancelQueueTicketRequest(
    Instant cancelledAt,
    String reasonCode,
    String note
) {
}
