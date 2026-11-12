package com.rpb.reservation.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaymentProofTemplateMigrationTest {

    @Test
    void migrationCreatesTenantScopedProofTemplateSeedLibrary() throws Exception {
        String migration = Files.readString(Path.of(
            "src/main/resources/db/migration/V054__paynow_payment_proof_template_library.sql"
        ));

        assertThat(migration)
            .contains("create table if not exists payment_proof_templates")
            .contains("create table if not exists payment_proof_template_samples")
            .contains("tenant_id uuid null")
            .contains("source in ('platform_seed', 'tenant_custom', 'tenant_override')")
            .contains("layout_json jsonb not null")
            .contains("payment.proof_template.manage")
            .contains("OCBC Chinese PayNow");
    }
}
