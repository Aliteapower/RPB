package com.rpb.reservation.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationPublicShareTokenMigrationTest {

    @Test
    void migrationCreatesStoreScopedPublicShareTokenTable() throws Exception {
        String migration = Files.readString(Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V017__reservation_public_share_tokens.sql"
        ));

        assertThat(migration)
            .contains("create table if not exists reservation_public_share_tokens")
            .contains("tenant_id uuid not null")
            .contains("store_id uuid not null")
            .contains("reservation_id uuid not null")
            .contains("token text not null")
            .contains("status text not null")
            .contains("expires_at timestamptz null")
            .contains("created_at timestamptz not null")
            .contains("updated_at timestamptz not null")
            .contains("constraint uq_reservation_public_share_tokens_token unique (token)")
            .contains("constraint fk_reservation_public_share_tokens_reservation_scope")
            .contains("references reservations(id, tenant_id, store_id)")
            .contains("constraint ck_reservation_public_share_tokens_status")
            .contains("status in ('active', 'revoked')")
            .contains("create unique index if not exists ux_reservation_public_share_tokens_active_reservation")
            .contains("where status = 'active'")
            .contains("create index if not exists ix_reservation_public_share_tokens_reservation");
    }
}
