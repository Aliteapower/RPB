package com.rpb.reservation.queue.application.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.idempotency.application.codec.JacksonIdempotencySnapshotCodec;
import org.springframework.stereotype.Component;

@Component
public final class SeatingFromCalledQueueSnapshotCodec
    extends JacksonIdempotencySnapshotCodec<SeatingFromCalledQueueSnapshot> {

    public SeatingFromCalledQueueSnapshotCodec(ObjectMapper objectMapper) {
        super(objectMapper, SeatingFromCalledQueueSnapshot.class);
    }
}
