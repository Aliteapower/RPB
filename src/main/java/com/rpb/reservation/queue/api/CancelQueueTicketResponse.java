package com.rpb.reservation.queue.api;

import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.List;
import java.util.UUID;

public record CancelQueueTicketResponse(
    boolean success,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID walkInId,
    String cancelledAt,
    String cancellationReasonCode,
    boolean alreadyCancelled,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {
}
