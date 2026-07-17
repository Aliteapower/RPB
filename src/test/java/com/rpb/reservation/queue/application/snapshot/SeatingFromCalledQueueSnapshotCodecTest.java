package com.rpb.reservation.queue.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeatingFromCalledQueueSnapshotCodecTest {

    private static final String LEGACY_PAYLOAD = """
        {"queueTicketId":"11111111-1111-1111-1111-111111111111","queueTicketNumber":22,"queueTicketStatus":"seated","reservationId":"22222222-2222-2222-2222-222222222222","reservationCode":"R-Q-SEAT-1","reservationStatus":"seated","seatingId":"33333333-3333-3333-3333-333333333333","resourceType":"dining_table","resourceId":"44444444-4444-4444-4444-444444444444","partySizeSnapshot":4,"seatingStatus":"occupied","seatingResourceStatus":"active","tableStatus":"occupied","groupMemberStatuses":[],"alreadySeated":false}
        """.trim();

    private final SeatingFromCalledQueueSnapshotCodec codec =
        new SeatingFromCalledQueueSnapshotCodec(new ObjectMapper());

    @Test
    void decodesLegacyReservationBackedQueuePayload() {
        SeatingFromCalledQueueSnapshot snapshot = codec.decode(LEGACY_PAYLOAD);

        assertThat(snapshot.queueTicketNumber()).isEqualTo(22);
        assertThat(snapshot.reservationCode()).isEqualTo("R-Q-SEAT-1");
        assertThat(snapshot.alreadySeated()).isFalse();
        assertThat(codec.encode(snapshot)).isEqualTo(LEGACY_PAYLOAD);
    }

    @Test
    void roundTripsWalkInBackedQueuePayloadWithNullReservationFields() {
        SeatingFromCalledQueueSnapshot snapshot = new SeatingFromCalledQueueSnapshot(
            UUID.randomUUID(), 23, "seated", null, null, null, UUID.randomUUID(),
            "table_group", UUID.randomUUID(), 5, "occupied", "active", null,
            List.of("occupied", "occupied"), true
        );

        SeatingFromCalledQueueSnapshot decoded = codec.decode(codec.encode(snapshot));

        assertThat(decoded.reservationId()).isNull();
        assertThat(decoded.reservationCode()).isNull();
        assertThat(decoded.groupMemberStatuses()).containsExactly("occupied", "occupied");
    }
}
