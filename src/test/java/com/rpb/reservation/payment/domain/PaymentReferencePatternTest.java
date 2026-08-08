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
    void ignoresLongBankTransactionIdWithoutSystemRefShape() {
        String raw = "Transaction ID 20260517TRBUSGSSGBRT7474840 amount SGD 0.10";

        assertThat(PaymentReferencePattern.extractSystemReference(raw)).isEmpty();
    }

    @Test
    void normalizesCaseWhitespaceAndHyphenSpacing() {
        assertThat(PaymentReferencePattern.normalize(" qp - 202608 - 0040 - 87d0 "))
            .isEqualTo("QP-202608-0040-87D0");
    }
}
