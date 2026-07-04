package com.rpb.reservation.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StoreShareEmailMigrationTest {

    @Test
    void migrationAddsNullableStoreShareEmail() throws Exception {
        String migration = Files.readString(Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V021__store_share_email.sql"
        ));

        assertThat(migration)
            .contains("add column if not exists share_email text null")
            .contains("constraint ck_stores_share_email")
            .contains("position('@' in share_email) > 1");
    }
}
