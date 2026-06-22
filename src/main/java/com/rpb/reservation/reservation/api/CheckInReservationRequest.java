package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record CheckInReservationRequest(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant arrivedAt,
    String reasonCode,
    String note
) {
}
