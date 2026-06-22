package com.rpb.reservation.idempotency.domain;

import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.idempotency.status.IdempotencyStatus;
import java.util.Objects;
import java.util.UUID;

/**
 * IdempotencyRecord domain skeleton. It deduplicates critical command intent
 * and is not a table lock replacement.
 */
public record IdempotencyRecord(
    UUID id,
    IdempotencyKey idempotencyKey,
    String source,
    String action,
    String requestHash,
    IdempotencyStatus status,
    String targetType,
    UUID targetId,
    String responseSnapshot
) {

    public IdempotencyRecord {
        Objects.requireNonNull(id, "idempotency_record_id_required");
        Objects.requireNonNull(idempotencyKey, "idempotency_key_required");
        Objects.requireNonNull(status, "idempotency_status_required");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("idempotency_source_required");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("idempotency_request_hash_required");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("idempotency_action_required");
        }
    }

    public IdempotencyRecord(UUID id, IdempotencyKey idempotencyKey, String action, IdempotencyStatus status) {
        this(id, idempotencyKey, "system", action, "legacy-request-hash", status, null, null, null);
    }

    public String completeIntent() {
        return "idempotency_record.complete.intent";
    }

    public String domainBoundary() {
        return "IdempotencyRecord deduplicates requests and is not a lock.";
    }
}
