package com.rpb.reservation.queue.api;

import java.time.Instant;

public record CallQueueTicketRequest(
    Instant calledAt,
    String reasonCode,
    String note
) {
}
