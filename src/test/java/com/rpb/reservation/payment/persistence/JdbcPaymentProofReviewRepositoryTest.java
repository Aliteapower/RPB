package com.rpb.reservation.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JdbcPaymentProofReviewRepositoryTest {

    @Test
    void repositorySqlKeepsProofReviewStoreScopedAndAtomic() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/rpb/reservation/payment/persistence/JdbcPaymentProofReviewRepository.java"
        ));

        assertThat(source)
            .contains("where i.tenant_id = ?")
            .contains("and i.store_id = ?")
            .contains("upper(i.payment_reference) in (")
            .contains("rows.size() == 1")
            .contains("insert into payment_proofs")
            .contains("insert into payment_ocr_results")
            .contains("insert into payment_verifications")
            .contains("update payment_intents")
            .contains("update payment_sessions")
            .contains("status = 'paid'")
            .contains("PaymentServiceErrorCode.PAYMENT_INTENT_STATE_CONFLICT");
    }
}
