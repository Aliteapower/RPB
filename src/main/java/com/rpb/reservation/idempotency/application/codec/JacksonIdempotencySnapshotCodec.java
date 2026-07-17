package com.rpb.reservation.idempotency.application.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

public abstract class JacksonIdempotencySnapshotCodec<T> implements IdempotencySnapshotCodec<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> snapshotType;

    protected JacksonIdempotencySnapshotCodec(ObjectMapper objectMapper, Class<T> snapshotType) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "object_mapper_required");
        this.snapshotType = Objects.requireNonNull(snapshotType, "snapshot_type_required");
    }

    @Override
    public final String encode(T snapshot) {
        if (snapshot == null) {
            throw new IdempotencySnapshotException("idempotency_snapshot_required");
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IdempotencySnapshotException("idempotency_snapshot_encode_failed", exception);
        }
    }

    @Override
    public final T decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IdempotencySnapshotException("idempotency_snapshot_payload_required");
        }
        try {
            T snapshot = objectMapper.readValue(payload, snapshotType);
            if (snapshot == null) {
                throw new IdempotencySnapshotException("idempotency_snapshot_decode_failed");
            }
            return snapshot;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IdempotencySnapshotException("idempotency_snapshot_decode_failed", exception);
        }
    }
}
