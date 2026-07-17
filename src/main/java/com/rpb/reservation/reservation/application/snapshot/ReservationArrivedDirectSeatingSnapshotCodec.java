package com.rpb.reservation.reservation.application.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.idempotency.application.codec.JacksonIdempotencySnapshotCodec;
import org.springframework.stereotype.Component;

@Component
public final class ReservationArrivedDirectSeatingSnapshotCodec
    extends JacksonIdempotencySnapshotCodec<ReservationArrivedDirectSeatingSnapshot> {

    public ReservationArrivedDirectSeatingSnapshotCodec(ObjectMapper objectMapper) {
        super(objectMapper, ReservationArrivedDirectSeatingSnapshot.class);
    }
}
