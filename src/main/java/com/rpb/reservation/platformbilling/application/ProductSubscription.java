package com.rpb.reservation.platformbilling.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductSubscription(
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
    List<ProductSubscriptionItem> items
) {
    public ProductSubscription {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public ProductSubscription(
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
        this(
            id,
            tenantId,
            appKey,
            productLineName,
            billingCycle,
            status,
            effectiveStatus,
            currentPeriodStart,
            currentPeriodEnd,
            amount,
            currency,
            paymentNote,
            entitlementStatus,
            entitlementValidUntil,
            createdAt,
            updatedAt,
            version,
            List.of()
        );
    }

    public ProductSubscription withEffectiveStatus(String nextEffectiveStatus) {
        return new ProductSubscription(
            id,
            tenantId,
            appKey,
            productLineName,
            billingCycle,
            status,
            nextEffectiveStatus,
            currentPeriodStart,
            currentPeriodEnd,
            amount,
            currency,
            paymentNote,
            entitlementStatus,
            entitlementValidUntil,
            createdAt,
            updatedAt,
            version,
            items
        );
    }

    public ProductSubscription withEntitlementState(String nextEntitlementStatus, OffsetDateTime nextEntitlementValidUntil) {
        return new ProductSubscription(
            id,
            tenantId,
            appKey,
            productLineName,
            billingCycle,
            status,
            effectiveStatus,
            currentPeriodStart,
            currentPeriodEnd,
            amount,
            currency,
            paymentNote,
            nextEntitlementStatus,
            nextEntitlementValidUntil,
            createdAt,
            updatedAt,
            version,
            items
        );
    }

    public ProductSubscription withCommercialState(
        String nextBillingCycle,
        String nextStatus,
        OffsetDateTime nextPeriodStart,
        OffsetDateTime nextPeriodEnd,
        BigDecimal nextAmount,
        String nextCurrency,
        String nextPaymentNote
    ) {
        int nextVersion = version + 1;
        return new ProductSubscription(
            id,
            tenantId,
            appKey,
            productLineName,
            nextBillingCycle,
            nextStatus,
            nextStatus,
            nextPeriodStart,
            nextPeriodEnd,
            nextAmount,
            nextCurrency,
            nextPaymentNote,
            entitlementStatus,
            nextPeriodEnd,
            createdAt,
            OffsetDateTime.now(),
            nextVersion,
            items
        );
    }

    public ProductSubscription withItems(List<ProductSubscriptionItem> nextItems) {
        return new ProductSubscription(
            id,
            tenantId,
            appKey,
            productLineName,
            billingCycle,
            status,
            effectiveStatus,
            currentPeriodStart,
            currentPeriodEnd,
            amount,
            currency,
            paymentNote,
            entitlementStatus,
            entitlementValidUntil,
            createdAt,
            updatedAt,
            version,
            nextItems
        );
    }
}
