package com.rpb.reservation.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.payment.domain.PaymentReferencePattern;
import java.math.BigDecimal;
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
    void detectsCommonSuccessKeywords() {
        assertThat(TesseractPaymentProofOcrAdapter.successDetected("Payment successful Comment QP-202608-0040-87D0")).isTrue();
        assertThat(TesseractPaymentProofOcrAdapter.successDetected("您已支付 0.10 SGD")).isTrue();
        assertThat(TesseractPaymentProofOcrAdapter.successDetected("Random transaction listing")).isFalse();
    }
}
