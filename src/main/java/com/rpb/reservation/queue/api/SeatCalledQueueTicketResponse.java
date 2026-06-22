package com.rpb.reservation.queue.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatCalledQueueTicketResponse(
    boolean success,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID seatingId,
    String seatingStatus,
    String resourceType,
    UUID resourceId,
    boolean alreadySeated,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {
}
