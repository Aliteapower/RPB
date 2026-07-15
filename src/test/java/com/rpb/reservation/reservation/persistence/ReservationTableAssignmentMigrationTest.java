package com.rpb.reservation.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationTableAssignmentMigrationTest {

    @Test
    void migrationEnforcesOneActiveAssignmentPerScopedReservation() throws Exception {
        String migration = Files.readString(Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V045__reservation_active_preassignment_uniqueness.sql"
        ));

        assertThat(migration)
            .contains("create unique index if not exists ux_reservation_preassignments_one_active_reservation")
            .contains("on reservation_preassignments (tenant_id, store_id, reservation_id)")
            .contains("where status = 'active' and deleted_at is null");
    }
}
