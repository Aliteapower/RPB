package com.rpb.reservation.platformbilling.application;

public record ProductSubscriptionMutationResult(
    boolean replayed,
    ProductSubscription subscription,
    SubscriptionQuote quote
) {
    public ProductSubscriptionMutationResult(boolean replayed, ProductSubscription subscription) {
        this(replayed, subscription, null);
    }
}
