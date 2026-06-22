package com.rpb.reservation.reservation.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.value.ReservationId;
import java.util.Objects;
import java.util.UUID;

/**
 * ReservationPreassignment domain skeleton. It is planned resource intent, not
 * final SeatingResource occupancy.
 */
public record ReservationPreassignment(
    UUID id,
    StoreScope scope,
    ReservationId reservationId,
    String resourceType,
    UUID resourceId,
    String status
) {

    public ReservationPreassignment {
        Objects.requireNonNull(id, "reservation_preassignment_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(reservationId, "reservation_id_required");
        Objects.requireNonNull(resourceId, "resource_id_required");
        requireText(resourceType, "resource_type_required");
        requireText(status, "preassignment_status_required");
    }

    public String releaseIntent() {
        return "reservation_preassignment.release.intent";
    }

    public String domainBoundary() {
        return "ReservationPreassignment is not final SeatingResource occupancy.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
