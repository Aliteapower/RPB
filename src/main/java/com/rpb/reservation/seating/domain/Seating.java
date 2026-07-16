package com.rpb.reservation.seating.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.seating.status.SeatingStatus;
import com.rpb.reservation.seating.value.SeatingId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Seating domain skeleton. Seating owns occupancy and is not CheckIn or
 * Reservation creation.
 */
public record Seating(
    SeatingId id,
    StoreScope scope,
    String sourceType,
    UUID sourceId,
    String seatingCode,
    String manualOverrideReasonCode,
    String note,
    PartySize partySizeSnapshot,
    SeatingStatus status,
    Instant completedAt
) {

    public Seating {
        Objects.requireNonNull(id, "seating_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(partySizeSnapshot, "party_size_snapshot_required");
        Objects.requireNonNull(status, "seating_status_required");
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("seating_source_type_required");
        }
    }

    public Seating(
        SeatingId id,
        StoreScope scope,
        String sourceType,
        UUID sourceId,
        String seatingCode,
        String manualOverrideReasonCode,
        String note,
        PartySize partySizeSnapshot,
        SeatingStatus status
    ) {
        this(
            id,
            scope,
            sourceType,
            sourceId,
            seatingCode,
            manualOverrideReasonCode,
            note,
            partySizeSnapshot,
            status,
            null
        );
    }

    public Seating(
        SeatingId id,
        StoreScope scope,
        String sourceType,
        UUID sourceId,
        String seatingCode,
        PartySize partySizeSnapshot,
        SeatingStatus status
    ) {
        this(id, scope, sourceType, sourceId, seatingCode, null, null, partySizeSnapshot, status, null);
    }

    public Seating(
        SeatingId id,
        StoreScope scope,
        String sourceType,
        PartySize partySizeSnapshot,
        SeatingStatus status
    ) {
        this(id, scope, sourceType, null, null, null, null, partySizeSnapshot, status, null);
    }

    public String occupyIntent() {
        return "seating.occupy.intent";
    }

    public String domainBoundary() {
        return "Seating creates occupancy and is not CheckIn or Reservation.";
    }
}
