package com.rpb.reservation.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StoreWhatsappShareProfileMigrationTest {

    @Test
    void migrationAddsNullableStoreWhatsappBusinessPhone() throws Exception {
        String migration = Files.readString(Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V020__store_whatsapp_share_profile.sql"
        ));

        assertThat(migration)
            .contains("alter table stores")
            .contains("add column if not exists whatsapp_business_phone_e164 text null")
            .contains("constraint ck_stores_whatsapp_business_phone_e164")
            .contains("whatsapp_business_phone_e164 ~ '^[+][1-9][0-9]{1,14}$'");
    }
}
