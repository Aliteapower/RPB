package com.rpb.reservation.cleaning.api;

public record CompleteCleaningRequest(
    String reasonCode,
    String note
) {
}
