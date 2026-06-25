package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueArrivedReservationResponse(
    boolean success,
    UUID reservationId,
    String reservationCode,
    String reservationStatus,
    UUID queueTicketId,
    int queueTicketNumber,
    String queueTicketDisplayNumber,
    String queueTicketStatus,
    UUID queueGroupId,
    String queueGroupCode,
    int partySize,
    String partySizeGroup,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    LocalDate businessDate,
    int queuePosition,
    boolean alreadyQueued,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {
}
