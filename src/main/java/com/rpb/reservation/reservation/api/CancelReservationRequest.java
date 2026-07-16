package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record CancelReservationRequest(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant cancelledAt,
    String reasonCode,
    String note
) {
}
