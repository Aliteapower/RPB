package com.rpb.reservation.reservation.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationArrivedDirectSeatingSnapshotCodecTest {

    private static final String LEGACY_PAYLOAD = """
        {"reservationId":"11111111-1111-1111-1111-111111111111","reservationCode":"R-SEAT-1","reservationStatus":"seated","seatingId":"22222222-2222-2222-2222-222222222222","resourceType":"dining_table","resourceId":"33333333-3333-3333-3333-333333333333","partySizeSnapshot":4,"seatingStatus":"occupied","seatingResourceStatus":"active","tableStatus":"occupied","groupMemberStatuses":[],"alreadySeated":false}
        """.trim();

    private final ReservationArrivedDirectSeatingSnapshotCodec codec =
        new ReservationArrivedDirectSeatingSnapshotCodec(new ObjectMapper());

    @Test
    void decodesLegacyPayloadAndWritesTheSameFieldContract() {
        ReservationArrivedDirectSeatingSnapshot snapshot = codec.decode(LEGACY_PAYLOAD);

        assertThat(snapshot.reservationId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(snapshot.reservationCode()).isEqualTo("R-SEAT-1");
        assertThat(snapshot.groupMemberStatuses()).isEmpty();
        assertThat(snapshot.alreadySeated()).isFalse();
        assertThat(codec.encode(snapshot)).isEqualTo(LEGACY_PAYLOAD);
    }

    @Test
    void preservesNullableTableStatusAndCopiesMemberStatuses() {
        ReservationArrivedDirectSeatingSnapshot snapshot = new ReservationArrivedDirectSeatingSnapshot(
            UUID.randomUUID(), "R-2", "seated", UUID.randomUUID(), "table_group", UUID.randomUUID(), 6,
            "occupied", "active", null, List.of("occupied", "occupied"), true
        );

        ReservationArrivedDirectSeatingSnapshot decoded = codec.decode(codec.encode(snapshot));

        assertThat(decoded.tableStatus()).isNull();
        assertThat(decoded.groupMemberStatuses()).containsExactly("occupied", "occupied");
        assertThat(decoded.alreadySeated()).isTrue();
    }
}
