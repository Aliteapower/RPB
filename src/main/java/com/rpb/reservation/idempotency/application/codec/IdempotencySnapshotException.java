package com.rpb.reservation.idempotency.application.codec;

public final class IdempotencySnapshotException extends RuntimeException {

    public IdempotencySnapshotException(String message) {
        super(message);
    }

    public IdempotencySnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
