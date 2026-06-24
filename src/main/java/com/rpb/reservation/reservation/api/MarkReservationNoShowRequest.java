package com.rpb.reservation.reservation.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record MarkReservationNoShowRequest(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant noShowAt,
    String reasonCode,
    String note
) {
}
