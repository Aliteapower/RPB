package com.rpb.reservation.idempotency.application.codec;

public interface IdempotencySnapshotCodec<T> {
    String encode(T snapshot);

    T decode(String payload);
}
