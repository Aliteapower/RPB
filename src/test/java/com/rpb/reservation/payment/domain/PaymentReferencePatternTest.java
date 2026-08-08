package com.rpb.reservation.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentReferencePatternTest {

    @Test
    void extractsPitReferenceFromChineseBankReceiptText() {
        String raw = """
            您已支付 0.10 SGD
            讯息
            PIT-202608-0021
            交易编号：2605160110303261
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw))
            .contains("PIT-202608-0021");
    }

    @Test
    void extractsQpReferenceWithChecksumFromCommentText() {
        String raw = """
            Payment successful
            You've sent S$0.10 to Stanley Teo
            Comment: "QP-202608-0040-87D0"
            Transaction ID 20260517TRBUSGSSGBRT7474840
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw))
            .contains("QP-202608-0040-87D0");
    }

    @Test
    void extractsLegacyReferenceWhenOcrUsesSpacesBetweenSegments() {
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP 202608 0013 2KZ6 您已支付 1.00 SGD"))
            .contains("QP-202608-0013-2KZ6");
    }

    @Test
    void extractsCompactOcrSafeReferenceFromChineseBankReceiptText() {
        String raw = """
            您已支付 1.00 SGD
            讯息
            QP2026080013AAHP
            交易编号：2608080118181271
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw))
            .contains("QP2026080013AAHP");
    }

    @Test
    void extractsCompactReferenceWhenOcrAddsSeparators() {
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP2026080013.AAHP 您已支付 1.00 SGD"))
            .contains("QP2026080013AAHP");
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP 202608 0013 AAHP 您已支付 1.00 SGD"))
            .contains("QP2026080013AAHP");
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP.202608.0013.AAHP 您已支付 1.00 SGD"))
            .contains("QP2026080013AAHP");
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP-202608-0013-AAHP 您已支付 1.00 SGD"))
            .contains("QP-202608-0013-AAHP");
    }

    @Test
    void extractsLegacySafeLetterReferenceWhenOcrUsesSpacesOrDots() {
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP 202608 0013 ACDE 您已支付 1.00 SGD"))
            .contains("QP2026080013ACDE");
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP.202608.0013.ACDE 您已支付 1.00 SGD"))
            .contains("QP2026080013ACDE");
    }

    @Test
    void producesCompactAndLegacyLookupVariantsForAmbiguousSafeLetterShape() {
        assertThat(PaymentReferencePattern.lookupVariants("QP 202608 0013 ACDE"))
            .containsExactly("QP2026080013ACDE", "QP-202608-0013-ACDE");
        assertThat(PaymentReferencePattern.lookupVariants("QP-202608-0013-AAHP"))
            .containsExactly("QP-202608-0013-AAHP", "QP2026080013AAHP");
    }

    @Test
    void skipsCompactShapedTransactionIdWithInvalidCheckAndFindsLaterReference() {
        String raw = "Transaction ID TR2608080118ACDE Ref QP2026080013AAHP";

        assertThat(PaymentReferencePattern.extractSystemReference(raw))
            .contains("QP2026080013AAHP");
    }

    @Test
    void skipsCompactCandidateWithInvalidPeriodAndFindsLaterReference() {
        String raw = "Transaction ID QP2026130013FYHC Ref QP2026080013AAHP";

        assertThat(PaymentReferencePattern.extractSystemReference(raw))
            .contains("QP2026080013AAHP");
    }

    @Test
    void ignoresLongBankTransactionIdWithoutSystemRefShape() {
        String raw = "Transaction ID 20260517TRBUSGSSGBRT7474840 amount SGD 0.10";

        assertThat(PaymentReferencePattern.extractSystemReference(raw)).isEmpty();
    }

    @Test
    void normalizesCaseWhitespaceAndHyphenSpacing() {
        assertThat(PaymentReferencePattern.normalize(" qp - 202608 - 0040 - 87d0 "))
            .isEqualTo("QP-202608-0040-87D0");
    }

    @Test
    void keepsHyphensForExplicitlySeparatedLegacyReference() {
        assertThat(PaymentReferencePattern.normalize("QP-202608-0013-ACDE"))
            .isEqualTo("QP-202608-0013-ACDE");
    }

    @Test
    void normalizesLegacyReferenceWhenOcrUsesSpacesBetweenSegments() {
        assertThat(PaymentReferencePattern.normalize("qp 202608 0013 2kz6"))
            .isEqualTo("QP-202608-0013-2KZ6");
    }

    @Test
    void normalizesCompactReferenceSeparators() {
        assertThat(PaymentReferencePattern.normalize(" qp.202608.0013.aahp "))
            .isEqualTo("QP2026080013AAHP");
    }
}
