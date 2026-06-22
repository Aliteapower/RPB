package com.rpb.reservation.queue.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallQueueTicketResponse(
    boolean success,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant calledAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant holdUntilAt,
    boolean alreadyCalled,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {
}
