package com.rpb.reservation.cleaning.domain;

import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.value.SeatingId;
import java.util.Objects;
import java.util.UUID;

/**
 * Cleaning domain skeleton. Cleaning owns resource cleanup status and is not
 * Turnover.
 */
public record Cleaning(
    CleaningId id,
    StoreScope scope,
    SeatingId seatingId,
    String resourceType,
    UUID resourceId,
    CleaningStatus status
) {

    public Cleaning {
        Objects.requireNonNull(id, "cleaning_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(seatingId, "seating_id_required");
        Objects.requireNonNull(resourceId, "resource_id_required");
        Objects.requireNonNull(status, "cleaning_status_required");
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("cleaning_resource_type_required");
        }
    }

    public String completeIntent() {
        return "cleaning.complete.intent";
    }

    public String domainBoundary() {
        return "Cleaning is resource status flow and is not Turnover.";
    }
}
