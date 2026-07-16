package com.rpb.reservation.platformbilling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.platformbilling.persistence.PlatformProductLinePriceRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubscriptionQuoteServiceTest {
    private final SubscriptionQuoteService service = new SubscriptionQuoteService(new FakePriceRepository());

    @Test
    void defaultsAmountFromPriceTimesDuration() {
        SubscriptionQuote quote = service.quote("reservation_queue", new BillingDuration("monthly", 3), 1, null, null);

        assertThat(quote.unitAmount()).isEqualByComparingTo("128.00");
        assertThat(quote.storeCount()).isEqualTo(1);
        assertThat(quote.storeUnitAmount()).isEqualByComparingTo("384.00");
        assertThat(quote.defaultAmount()).isEqualByComparingTo("384.00");
        assertThat(quote.finalAmount()).isEqualByComparingTo("384.00");
        assertThat(quote.currency()).isEqualTo("SGD");
    }

    @Test
    void defaultsAmountFromPriceTimesDurationAndStoreCount() {
        SubscriptionQuote quote = service.quote("reservation_queue", new BillingDuration("monthly", 3), 2, null, null);

        assertThat(quote.storeCount()).isEqualTo(2);
        assertThat(quote.storeUnitAmount()).isEqualByComparingTo("384.00");
        assertThat(quote.defaultAmount()).isEqualByComparingTo("768.00");
        assertThat(quote.finalAmount()).isEqualByComparingTo("768.00");
    }

    @Test
    void manualAmountOverrideKeepsDefaultSnapshot() {
        SubscriptionQuote quote = service.quote("reservation_queue", new BillingDuration("yearly", 2), 1, new BigDecimal("1999.00"), "sgd");

        assertThat(quote.defaultAmount()).isEqualByComparingTo("2400.00");
        assertThat(quote.finalAmount()).isEqualByComparingTo("1999.00");
        assertThat(quote.eventPayloadJson()).contains("\"defaultAmount\": \"2400\"").contains("\"finalAmount\": \"1999\"");
    }

    @Test
    void rejectsMissingActivePrice() {
        assertThatThrownBy(() -> service.quote("missing_app", new BillingDuration("monthly", 1), 1, null, null))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.REQUEST_INVALID.name());
    }

    private static final class FakePriceRepository implements PlatformProductLinePriceRepository {
        @Override
        public List<PlatformProductLinePrice> findByAppKeys(Collection<String> appKeys) {
            return List.of();
        }

        @Override
        public List<PlatformProductLinePrice> replacePrices(String appKey, List<PlatformProductLinePriceUpdate> prices) {
            return List.of();
        }

        @Override
        public Optional<PlatformProductLinePrice> findActivePrice(String appKey, String billingCycle) {
            if ("yearly".equals(billingCycle)) {
                return Optional.of(new PlatformProductLinePrice(appKey, "yearly", new BigDecimal("1200.00"), "SGD", "active", 0));
            }
            if ("monthly".equals(billingCycle) && "reservation_queue".equals(appKey)) {
                return Optional.of(new PlatformProductLinePrice(appKey, "monthly", new BigDecimal("128.00"), "SGD", "active", 0));
            }
            return Optional.empty();
        }
    }
}
