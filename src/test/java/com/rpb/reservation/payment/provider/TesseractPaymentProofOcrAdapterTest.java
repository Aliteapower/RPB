package com.rpb.reservation.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.payment.application.PaymentProofOcrExpected;
import com.rpb.reservation.payment.domain.PaymentReferencePattern;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TesseractPaymentProofOcrAdapterTest {

    @Test
    void parserExtractsOcbcChineseReceiptReferenceAndAmount() {
        String raw = """
            您已支付 0.10 SGD
            至
            ZHANG XIANLI
            讯息
            RCP-202605-0023
            转账日期
            16 May 2026
            交易编号：2605160110303261
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw)).contains("RCP-202605-0023");
        assertThat(TesseractPaymentProofOcrAdapter.extractAmount(raw, new BigDecimal("0.10")))
            .contains(new BigDecimal("0.10"));
    }

    @Test
    void parserExtractsEnglishCommentReferenceAndAmount() {
        String raw = """
            Payment successful
            You've sent S$0.10 to Stanley Teo
            17 May 2026 at 19:50
            Comment: "HT-202605-0012"
            Transaction ID 20260517TRBUSGSSGBRT7474840
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw)).contains("HT-202605-0012");
        assertThat(TesseractPaymentProofOcrAdapter.extractAmount(raw, new BigDecimal("0.10")))
            .contains(new BigDecimal("0.10"));
    }

    @Test
    void parserExtractsReferenceWhenOcrAddsSpacesInsideSerial() {
        String raw = """
            讯息
            QP-202608-001 3-2KZ6
            您已支付 1.00 SGD
            """;

        assertThat(PaymentReferencePattern.extractSystemReference(raw)).contains("QP-202608-0013-2KZ6");
        assertThat(TesseractPaymentProofOcrAdapter.extractAmount(raw, new BigDecimal("1.00")))
            .contains(new BigDecimal("1.00"));
    }

    @Test
    void parserExtractsReferenceWhenOcrReadsSeparatorsAsDots() {
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP-202608-0013.2Kz6 您已支付 1.00 SGD"))
            .contains("QP-202608-0013-2KZ6");
        assertThat(PaymentReferencePattern.extractSystemReference("讯息 QP-202608.0013.2Kz6 您已支付 1.00 SGD"))
            .contains("QP-202608-0013-2KZ6");
    }

    @Test
    void selectsBestOcrAttemptWhenSparseModeFindsExactExpectedReference() {
        List<String> attempts = List.of(
            """
                您 已 支付 1.00 SGD
                QP-202608-0013-2K7
                """,
            """
                您
                已 支付 1.00 SGD
                讯息
                QP-202608-001 3-2KZ6
                """
        );

        assertThat(TesseractPaymentProofOcrAdapter.selectBestFields(
            attempts,
            new PaymentProofOcrExpected("QP-202608-0013-2KZ6", new BigDecimal("1.00"))
        ).extractedReference()).isEqualTo("QP-202608-0013-2KZ6");
    }

    @Test
    void selectsMoreCompleteReferenceWhenNoExpectedReferenceIsAvailable() {
        List<String> attempts = List.of(
            """
                您 已 支付 1.00 SGD
                QP-202608-0013-2K7
                """,
            """
                您
                已 支付 1.00 SGD
                讯息
                QP-202608-001 3-2KZ6
                """
        );

        assertThat(TesseractPaymentProofOcrAdapter.selectBestFields(
            attempts,
            new PaymentProofOcrExpected(null, null)
        ).extractedReference()).isEqualTo("QP-202608-0013-2KZ6");
    }

    @Test
    void detectsCommonSuccessKeywords() {
        assertThat(TesseractPaymentProofOcrAdapter.successDetected("Payment successful Comment QP-202608-0040-87D0")).isTrue();
        assertThat(TesseractPaymentProofOcrAdapter.successDetected("您已支付 0.10 SGD")).isTrue();
        assertThat(TesseractPaymentProofOcrAdapter.successDetected("Random transaction listing")).isFalse();
    }
}
