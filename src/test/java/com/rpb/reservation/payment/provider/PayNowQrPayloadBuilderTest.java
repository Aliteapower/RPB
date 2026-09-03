package com.rpb.reservation.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayNowQrPayloadBuilderTest {

    @Test
    void buildsFixedAmountPayNowPayloadWithReference() {
        PayNowQrPayloadBuilder builder = new PayNowQrPayloadBuilder();

        String payload = builder.build(new PayNowQrPayloadRequest(
            "uen",
            null,
            "202012345A",
            "RPB Demo Restaurant",
            new BigDecimal("18.8"),
            "QP-202608-0001-A1B2"
        ));

        assertThat(payload).contains("202012345A");
        assertThat(payload).contains("RPB Demo Restaurant");
        assertThat(payload).contains("18.80");
        assertThat(payload).contains("QP-202608-0001-A1B2");
    }

    @Test
    void rejectsMissingPayNowProxyValue() {
        PayNowQrPayloadBuilder builder = new PayNowQrPayloadBuilder();

        assertThatThrownBy(() -> builder.build(new PayNowQrPayloadRequest(
            "mobile",
            " ",
            null,
            "RPB Demo Restaurant",
            new BigDecimal("18.80"),
            "QP-202608-0001-A1B2"
        ))).hasMessageContaining(PaymentServiceErrorCode.PAYMENT_PROFILE_INVALID.name());
    }

    @Test
    void prefixesSingaporeMobileProxyWithCountryCode() {
        PayNowQrPayloadBuilder builder = new PayNowQrPayloadBuilder();

        String payload = builder.build(new PayNowQrPayloadRequest(
            "mobile",
            "8735 7878",
            null,
            "RPB Demo Restaurant",
            new BigDecimal("0.10"),
            "QP-TEST-010"
        ));

        assertThat(payload).contains("+6587357878");
        assertThat(payload).contains("0.10");
        assertThat(payload).contains("QP-TEST-010");
    }
}
