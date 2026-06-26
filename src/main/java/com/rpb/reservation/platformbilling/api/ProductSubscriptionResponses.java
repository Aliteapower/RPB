package com.rpb.reservation.platformbilling.api;

import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionMutationResult;
import com.rpb.reservation.platformbilling.application.SubscriptionQuote;
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
    ProductSubscriptionItemResponse subscription,
    ProductSubscriptionQuoteResponse quote
) {
    static ProductSubscriptionResponse from(ProductSubscriptionMutationResult result) {
        return new ProductSubscriptionResponse(
            true,
            result.replayed(),
            ProductSubscriptionItemResponse.from(result.subscription()),
            ProductSubscriptionQuoteResponse.from(result.quote())
        );
    }
}

record ProductSubscriptionQuoteResponse(
    Integer durationCount,
    String durationUnit,
    BigDecimal unitAmount,
    BigDecimal defaultAmount,
    BigDecimal finalAmount,
    String currency
) {
    static ProductSubscriptionQuoteResponse from(SubscriptionQuote quote) {
        if (quote == null) {
            return null;
        }
        return new ProductSubscriptionQuoteResponse(
            quote.durationCount(),
            quote.durationUnit(),
            quote.unitAmount(),
            quote.defaultAmount(),
            quote.finalAmount(),
            quote.currency()
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
