package com.rpb.reservation.walkin.application.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.idempotency.application.codec.JacksonIdempotencySnapshotCodec;
import org.springframework.stereotype.Component;

@Component
public final class WalkInDirectSeatingSnapshotCodec
    extends JacksonIdempotencySnapshotCodec<WalkInDirectSeatingSnapshot> {

    public WalkInDirectSeatingSnapshotCodec(ObjectMapper objectMapper) {
        super(objectMapper, WalkInDirectSeatingSnapshot.class);
    }
}
