package com.rpb.reservation.table.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.IdempotencyKey;
import com.rpb.reservation.table.status.TableLockStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * TableLock domain skeleton. It records lock intent and is not final occupancy.
 */
public record TableLock(
    UUID id,
    StoreScope scope,
    String resourceType,
    UUID resourceId,
    String lockKey,
    String lockOwner,
    String sourceType,
    UUID sourceId,
    Instant lockedAt,
    Instant lockedUntilAt,
    IdempotencyKey idempotencyKey,
    TableLockStatus status
) {

    public TableLock {
        Objects.requireNonNull(id, "table_lock_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(resourceId, "table_lock_resource_id_required");
        Objects.requireNonNull(lockedAt, "locked_at_required");
        Objects.requireNonNull(lockedUntilAt, "locked_until_at_required");
        Objects.requireNonNull(status, "table_lock_status_required");
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("table_lock_resource_type_required");
        }
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("table_lock_key_required");
        }
        if (lockOwner == null || lockOwner.isBlank()) {
            throw new IllegalArgumentException("table_lock_owner_required");
        }
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("table_lock_source_type_required");
        }
    }

    public TableLock(
        UUID id,
        StoreScope scope,
        String resourceType,
        UUID resourceId,
        Instant lockedUntilAt,
        IdempotencyKey idempotencyKey,
        TableLockStatus status
    ) {
        this(
            id,
            scope,
            resourceType,
            resourceId,
            "lock-" + id,
            "system",
            "system",
            null,
            Instant.EPOCH,
            lockedUntilAt,
            idempotencyKey,
            status
        );
    }

    public String releaseIntent() {
        return "table_lock.release.intent";
    }

    public String domainBoundary() {
        return "TableLock is not final Seating occupancy.";
    }
}
