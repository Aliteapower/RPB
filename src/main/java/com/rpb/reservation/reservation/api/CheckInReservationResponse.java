package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rpb.reservation.walkin.api.ApiIdempotencyResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckInReservationResponse(
    boolean success,
    UUID reservationId,
    String reservationCode,
    String status,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant arrivedAt,
    boolean alreadyArrived,
    List<String> events,
    ApiIdempotencyResponse idempotency
) {
}
