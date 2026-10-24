package com.rpb.reservation.payment.application;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentReceiptIdentifiersTest {
    @Test void separatesWalletReferenceBankReferenceAndRecipient() {
        var fields=PaymentReceiptIdentifiers.parse("Payment successful\nMessage: W0123456789abcdefabcd\nTransaction ID: 2605160110303261\nPayNow UEN: 202012345A\n");
        assertThat(fields.systemReference()).isEqualTo("W0123456789ABCDEFABCD");
        assertThat(fields.bankReference()).isEqualTo("2605160110303261");
        assertThat(fields.recipient()).isEqualTo("202012345A");
        assertThat(fields.failedOrPending()).isFalse();
    }
    @Test void ambiguousReferencesNeverPickAnArbitraryValueAndFailedOverridesSuccess() {
        var fields=PaymentReceiptIdentifiers.parse("Transaction Ref: BANK-1\nTransaction ID: BANK-2\nPayment successful\nStatus: pending");
        assertThat(fields.bankReference()).isNull();assertThat(fields.systemReference()).isNull();assertThat(fields.failedOrPending()).isTrue();
    }
    @Test void bankReferenceIsNotTheSystemReferenceAndPayerMobileIsNotRecipient() {
        var fields=PaymentReceiptIdentifiers.parse("Transaction ID: W0123456789abcdefabcd\nSender mobile: +6591234567\n");
        assertThat(fields.systemReference()).isNull();assertThat(fields.recipient()).isNull();
    }
}
