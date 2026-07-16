package com.rpb.reservation.platformbilling.application;

public record ProductSubscriptionStatusCommand(
    String idempotencyKey,
    String paymentNote,
    Integer version
) {
}
