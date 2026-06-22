package com.rpb.reservation.reservation.api;

public record QueueArrivedReservationRequest(
    String partySizeGroup,
    String reasonCode,
    String note
) {
}
