package com.rpb.reservation.walkin.application.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalkInDirectSeatingSnapshotCodecTest {

    private static final String LEGACY_PAYLOAD = """
        {"walkInId":"11111111-1111-1111-1111-111111111111","seatingId":"22222222-2222-2222-2222-222222222222","resourceType":"dining_table","resourceId":"33333333-3333-3333-3333-333333333333","partySizeSnapshot":2}
        """.trim();

    private final WalkInDirectSeatingSnapshotCodec codec =
        new WalkInDirectSeatingSnapshotCodec(new ObjectMapper());

    @Test
    void decodesLegacyPayloadAndWritesTheSameFieldContract() {
        WalkInDirectSeatingSnapshot snapshot = codec.decode(LEGACY_PAYLOAD);

        assertThat(snapshot.walkInId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(snapshot.partySizeSnapshot()).isEqualTo(2);
        assertThat(codec.encode(snapshot)).isEqualTo(LEGACY_PAYLOAD);
    }
}
