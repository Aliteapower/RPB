package com.rpb.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.table.status.DiningTableStatus;
import com.rpb.reservation.table.status.TableGroupStatus;
import com.rpb.reservation.table.status.TableLockStatus;
import com.rpb.reservation.turnover.status.TurnoverStatus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StatusEnumConsistencyTest {

    @Test
    void reservationStatusCodesMatchGovernanceAndMigration() {
        assertThat(codes(ReservationStatus.values()))
            .containsExactly("draft", "confirmed", "arrived", "seated", "completed", "cancelled", "no_show");
    }

    @Test
    void queueTicketStatusCodesMatchGovernanceAndMigration() {
        assertThat(codes(QueueTicketStatus.values()))
            .containsExactly("waiting", "called", "skipped", "rejoined", "seated", "cancelled", "expired");
    }

    @Test
    void tableStatusCodesMatchGovernanceAndMigration() {
        assertThat(codes(DiningTableStatus.values()))
            .containsExactly("available", "locked", "reserved", "occupied", "cleaning", "inactive");
    }

    @Test
    void otherStatusCodesMatchMigrationBoundaries() {
        assertThat(codes(SeatingStatus.values()))
            .containsExactly("planned", "locked", "occupied", "completed", "cleaning_triggered", "cancelled");
        assertThat(codes(CleaningStatus.values()))
            .containsExactly("pending", "cleaning", "completed", "released", "cancelled");
        assertThat(codes(TurnoverStatus.values()))
            .containsExactly("pending", "recorded", "archived");
        assertThat(codes(TableLockStatus.values()))
            .containsExactly("active", "released", "expired", "cancelled");
        assertThat(codes(IdempotencyStatus.values()))
            .containsExactly("started", "completed", "failed", "expired");
        assertThat(codes(TableGroupStatus.values()))
            .containsExactly("created", "active", "inactive", "deleted", "locked", "occupied", "released", "ended");
    }

    private static String[] codes(Enum<?>[] statuses) {
        return Arrays.stream(statuses)
            .map(status -> {
                try {
                    return (String) status.getClass().getMethod("code").invoke(status);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            })
            .toArray(String[]::new);
    }
}
