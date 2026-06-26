package com.rpb.reservation.platformbilling.application;

public record ProductSubscriptionMutationResult(
    boolean replayed,
    ProductSubscription subscription
) {
}
