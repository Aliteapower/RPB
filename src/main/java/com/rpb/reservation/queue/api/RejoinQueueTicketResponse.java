package com.rpb.reservation.queue.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RejoinQueueTicketResponse(
    boolean success,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketStatus,
    Integer queuePosition,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant rejoinedAt,
    boolean alreadyRejoined,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {
}
