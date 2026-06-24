package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record CompleteReservationRequest(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant completedAt,
    String reasonCode,
    String note
) {
}
