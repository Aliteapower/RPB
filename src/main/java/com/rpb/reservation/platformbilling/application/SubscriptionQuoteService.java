package com.rpb.reservation.platformbilling.application;

import com.rpb.reservation.platformbilling.persistence.PlatformProductLinePriceRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionQuoteService {
    private static final String PRICE_SOURCE = "platform_product_line_prices";

    private final PlatformProductLinePriceRepository prices;

    public SubscriptionQuoteService(PlatformProductLinePriceRepository prices) {
        this.prices = prices;
    }

    public SubscriptionQuote quote(
        String appKey,
        BillingDuration duration,
        int storeCount,
        BigDecimal requestedAmount,
        String requestedCurrency
    ) {
        PlatformProductLinePrice price = prices.findActivePrice(appKey, duration.billingCycle())
            .orElseThrow(() -> new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID));
        int safeStoreCount = Math.max(0, storeCount);
        BigDecimal storeUnitAmount = price.amount().multiply(BigDecimal.valueOf(duration.durationCount()));
        BigDecimal defaultAmount = storeUnitAmount.multiply(BigDecimal.valueOf(safeStoreCount));
        BigDecimal finalAmount = requestedAmount == null ? defaultAmount : requestedAmount;
        if (finalAmount.signum() < 0) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String currency = requestedCurrency == null || requestedCurrency.isBlank()
            ? price.currency()
            : requestedCurrency.trim().toUpperCase();
        if (currency.length() != 3) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return new SubscriptionQuote(
            duration.durationCount(),
            duration.durationUnit(),
            safeStoreCount,
            price.amount(),
            storeUnitAmount,
            defaultAmount,
            finalAmount,
            currency,
            PRICE_SOURCE
        );
    }
}
