package com.rpb.reservation.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaymentProofReviewMigrationTest {

    @Test
    void proofReviewPermissionIsDocumentedForV053() throws Exception {
        String migration = Files.readString(Path.of(
            "src/main/resources/db/migration/V053__paynow_payment_proof_review_permissions.sql"
        ));

        assertThat(migration)
            .contains("payment.proof.review")
            .contains("payment")
            .contains("store_staff")
            .contains("store_manager")
            .contains("tenant_admin");
    }
}
