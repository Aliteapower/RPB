package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionMutationResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record ProductSubscriptionListResponse(
    boolean success,
    List<ProductSubscriptionItemResponse> subscriptions
) {
    static ProductSubscriptionListResponse from(List<ProductSubscription> subscriptions) {
        return new ProductSubscriptionListResponse(
            true,
            subscriptions.stream().map(ProductSubscriptionItemResponse::from).toList()
        );
    }
}

record ProductSubscriptionResponse(
    boolean success,
    boolean replayed,
    ProductSubscriptionItemResponse subscription
) {
    static ProductSubscriptionResponse from(ProductSubscriptionMutationResult result) {
        return new ProductSubscriptionResponse(
            true,
            result.replayed(),
            ProductSubscriptionItemResponse.from(result.subscription())
        );
    }
}

record ProductSubscriptionItemResponse(
    UUID id,
    UUID tenantId,
    String appKey,
    String productLineName,
    String billingCycle,
    String status,
    String effectiveStatus,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    BigDecimal amount,
    String currency,
    String paymentNote,
    String entitlementStatus,
    OffsetDateTime entitlementValidUntil,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int version
) {
    static ProductSubscriptionItemResponse from(ProductSubscription subscription) {
        return new ProductSubscriptionItemResponse(
            subscription.id(),
            subscription.tenantId(),
            subscription.appKey(),
            subscription.productLineName(),
            subscription.billingCycle(),
            subscription.status(),
            subscription.effectiveStatus(),
            subscription.currentPeriodStart(),
            subscription.currentPeriodEnd(),
            subscription.amount(),
            subscription.currency(),
            subscription.paymentNote(),
            subscription.entitlementStatus(),
            subscription.entitlementValidUntil(),
            subscription.createdAt(),
            subscription.updatedAt(),
            subscription.version()
        );
    }
}
