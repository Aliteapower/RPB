package com.rpb.reservation.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PaymentReferenceGeneratorTest {

    @Test
    void generatesCompactReferenceWithOcrSafeCheckSegment() {
        String result = PaymentReferenceGenerator.generate("QP", YearMonth.of(2026, 8), 40);

        assertThat(result).matches("QP2026080040[ACDEFGHJKMNPQRTVWXY]{4}");
        assertThat(result).doesNotContain("-", ".", "O", "I", "L", "B", "S", "Z");
    }

    @Test
    void supportsPitPrefixForCompactReference() {
        String result = PaymentReferenceGenerator.generate("PIT", YearMonth.of(2026, 8), 21);

        assertThat(result).matches("PIT2026080021[ACDEFGHJKMNPQRTVWXY]{4}");
    }

    @Test
    void compactReferenceIsDeterministicForSameInputs() {
        String first = PaymentReferenceGenerator.generate("QP", YearMonth.of(2026, 8), 13);
        String second = PaymentReferenceGenerator.generate("qp", YearMonth.of(2026, 8), 13);

        assertThat(first).isEqualTo("QP2026080013AAHP");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void validatesCompactReferencePeriodSequenceAndCheckSegment() {
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP2026080013AAHP")).isTrue();
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP2026080013AAHA")).isFalse();
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP2026130013FYHC")).isFalse();
    }

    @Test
    void rejectsPrefixLongerThanEightCharacters() {
        assertThatThrownBy(() -> PaymentReferenceGenerator.generate("TOO-LONG", YearMonth.of(2026, 8), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payment_reference_prefix_invalid");
    }

    @Test
    void rejectsSequenceThatExceedsFourDigits() {
        assertThatThrownBy(() -> PaymentReferenceGenerator.generate("QP", YearMonth.of(2026, 8), 10000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payment_reference_sequence_invalid");
    }
}
