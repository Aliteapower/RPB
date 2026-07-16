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
    List<ProductSubscriptionSummaryResponse> subscriptions
) {
    static ProductSubscriptionListResponse from(List<ProductSubscription> subscriptions) {
        return new ProductSubscriptionListResponse(
            true,
            subscriptions.stream().map(ProductSubscriptionSummaryResponse::from).toList()
        );
    }
}

record ProductSubscriptionResponse(
    boolean success,
    boolean replayed,
    ProductSubscriptionSummaryResponse subscription,
    ProductSubscriptionQuoteResponse quote
) {
    static ProductSubscriptionResponse from(ProductSubscriptionMutationResult result) {
        return new ProductSubscriptionResponse(
            true,
            result.replayed(),
            ProductSubscriptionSummaryResponse.from(result.subscription()),
            ProductSubscriptionQuoteResponse.from(result.quote())
        );
    }
}

record ProductSubscriptionQuoteResponse(
    Integer durationCount,
    String durationUnit,
    Integer storeCount,
    BigDecimal unitAmount,
    BigDecimal storeUnitAmount,
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
            quote.storeCount(),
            quote.unitAmount(),
            quote.storeUnitAmount(),
            quote.defaultAmount(),
            quote.finalAmount(),
            quote.currency()
        );
    }
}

record ProductSubscriptionSummaryResponse(
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
    int version,
    List<ProductSubscriptionBillingItemResponse> items
) {
    static ProductSubscriptionSummaryResponse from(ProductSubscription subscription) {
        return new ProductSubscriptionSummaryResponse(
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
            subscription.version(),
            subscription.items().stream().map(ProductSubscriptionBillingItemResponse::from).toList()
        );
    }
}

record ProductSubscriptionBillingItemResponse(
    UUID id,
    String scopeType,
    UUID storeId,
    String storeCode,
    String storeName,
    UUID operatingEntityId,
    String operatingEntityName,
    String billingCycle,
    OffsetDateTime currentPeriodStart,
    OffsetDateTime currentPeriodEnd,
    int quantity,
    BigDecimal unitAmount,
    BigDecimal amount,
    String currency,
    String status,
    String paymentNote,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int version
) {
    static ProductSubscriptionBillingItemResponse from(com.rpb.reservation.platformbilling.application.ProductSubscriptionItem item) {
        return new ProductSubscriptionBillingItemResponse(
            item.id(),
            item.scopeType(),
            item.storeId(),
            item.storeCode(),
            item.storeName(),
            item.operatingEntityId(),
            item.operatingEntityName(),
            item.billingCycle(),
            item.currentPeriodStart(),
            item.currentPeriodEnd(),
            item.quantity(),
            item.unitAmount(),
            item.amount(),
            item.currency(),
            item.status(),
            item.paymentNote(),
            item.createdAt(),
            item.updatedAt(),
            item.version()
        );
    }
}
