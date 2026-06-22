package com.rpb.reservation.queue.api;

import java.time.Instant;

public record SkipQueueTicketRequest(
    Instant skippedAt,
    String reasonCode,
    String note
) {
}
