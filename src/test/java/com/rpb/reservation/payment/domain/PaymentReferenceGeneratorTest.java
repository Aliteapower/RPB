package com.rpb.reservation.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentReferenceGeneratorTest {

    @Test
    void generatesCompactReferenceWithOcrSafeCheckSegment() {
        String result = PaymentReferenceGenerator.generate("QP", LocalDate.of(2026, 8, 8), 40);

        assertThat(result).matches("QP202608080040[ACDEFGHJKMNPQRTVWXY]{4}");
        assertThat(result).doesNotContain("-", ".", "O", "I", "L", "B", "S", "Z");
    }

    @Test
    void supportsPitPrefixForCompactReference() {
        String result = PaymentReferenceGenerator.generate("PIT", LocalDate.of(2026, 8, 8), 21);

        assertThat(result).matches("PIT202608080021[ACDEFGHJKMNPQRTVWXY]{4}");
    }

    @Test
    void compactReferenceIsDeterministicForSameInputs() {
        String first = PaymentReferenceGenerator.generate("QP", LocalDate.of(2026, 8, 8), 13);
        String second = PaymentReferenceGenerator.generate("qp", LocalDate.of(2026, 8, 8), 13);

        assertThat(first).isEqualTo("QP202608080013YGDN");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void validatesCompactReferencePeriodSequenceAndCheckSegment() {
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP202608080013YGDN")).isTrue();
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP202608080013YGDA")).isFalse();
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP2026080013AAHP")).isTrue();
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP2026080013AAHA")).isFalse();
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP2026130013FYHC")).isFalse();
        assertThat(PaymentReferenceGenerator.isValidCompactReference("QP202602300013ACDE")).isFalse();
    }

    @Test
    void rejectsPrefixLongerThanEightCharacters() {
        assertThatThrownBy(() -> PaymentReferenceGenerator.generate("TOO-LONG", LocalDate.of(2026, 8, 8), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payment_reference_prefix_invalid");
    }

    @Test
    void rejectsSequenceThatExceedsFourDigits() {
        assertThatThrownBy(() -> PaymentReferenceGenerator.generate("QP", LocalDate.of(2026, 8, 8), 10000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payment_reference_sequence_invalid");
    }
}
