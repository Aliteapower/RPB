package com.rpb.reservation.idempotency.application.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JacksonIdempotencySnapshotCodecTest {

    private final ExampleCodec codec = new ExampleCodec(new ObjectMapper());

    @Test
    void roundTripsRecordsUsingCompactJson() {
        ExampleSnapshot snapshot = new ExampleSnapshot(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "seated",
            List.of("occupied", "occupied")
        );

        String payload = codec.encode(snapshot);

        assertThat(payload).isEqualTo(
            "{\"id\":\"11111111-1111-1111-1111-111111111111\",\"status\":\"seated\",\"memberStatuses\":[\"occupied\",\"occupied\"]}"
        );
        assertThat(codec.decode(payload)).isEqualTo(snapshot);
    }

    @Test
    void rejectsBlankAndMalformedPayloadsWithOneFailureType() {
        assertThatThrownBy(() -> codec.decode(" "))
            .isInstanceOf(IdempotencySnapshotException.class)
            .hasMessage("idempotency_snapshot_payload_required");
        assertThatThrownBy(() -> codec.decode("{not-json}"))
            .isInstanceOf(IdempotencySnapshotException.class)
            .hasMessage("idempotency_snapshot_decode_failed");
    }

    private record ExampleSnapshot(UUID id, String status, List<String> memberStatuses) {
    }

    private static final class ExampleCodec extends JacksonIdempotencySnapshotCodec<ExampleSnapshot> {
        private ExampleCodec(ObjectMapper objectMapper) {
            super(objectMapper, ExampleSnapshot.class);
        }
    }
}
