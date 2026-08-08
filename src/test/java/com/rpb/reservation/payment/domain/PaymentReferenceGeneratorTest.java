package com.rpb.reservation.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PaymentReferenceGeneratorTest {

    @Test
    void generatesPrefixPeriodSequenceAndCheckSegment() {
        String result = PaymentReferenceGenerator.generate("QP", YearMonth.of(2026, 8), 40);

        assertThat(result).matches("QP-202608-0040-[A-Z0-9]{4}");
    }

    @Test
    void supportsPitPrefixForCanonicalReference() {
        String result = PaymentReferenceGenerator.generate("PIT", YearMonth.of(2026, 8), 21);

        assertThat(result).matches("PIT-202608-0021-[A-Z0-9]{4}");
    }

    @Test
    void rejectsPrefixLongerThanEightCharacters() {
        assertThatThrownBy(() -> PaymentReferenceGenerator.generate("TOO-LONG", YearMonth.of(2026, 8), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payment_reference_prefix_invalid");
    }
}
