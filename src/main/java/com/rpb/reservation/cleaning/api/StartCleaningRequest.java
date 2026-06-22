package com.rpb.reservation.cleaning.api;

public record StartCleaningRequest(
    String reasonCode,
    String note
) {
}
