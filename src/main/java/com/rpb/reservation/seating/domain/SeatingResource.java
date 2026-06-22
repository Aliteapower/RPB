package com.rpb.reservation.seating.domain;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.value.SeatingId;
import java.util.Objects;
import java.util.UUID;

/**
 * SeatingResource domain skeleton. It assigns a resource to Seating and does
 * not validate the source flow by itself.
 */
public record SeatingResource(UUID id, StoreScope scope, SeatingId seatingId, String resourceType, UUID resourceId, String status) {

    public SeatingResource {
        Objects.requireNonNull(id, "seating_resource_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        Objects.requireNonNull(seatingId, "seating_id_required");
        Objects.requireNonNull(resourceId, "resource_id_required");
        requireText(resourceType, "resource_type_required");
        requireText(status, "seating_resource_status_required");
    }

    public String releaseIntent() {
        return "seating_resource.release.intent";
    }

    public String domainBoundary() {
        return "SeatingResource is a resource assignment, not the Seating source.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
