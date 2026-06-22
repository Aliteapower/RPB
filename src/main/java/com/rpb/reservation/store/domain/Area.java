package com.rpb.reservation.store.domain;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.Objects;
import java.util.UUID;

/**
 * Area domain skeleton. Area zones tables and does not own customers, queue
 * numbers, or reservation capacity by itself.
 */
public record Area(UUID id, StoreScope scope, String areaCode, String displayName, String status) {

    public Area {
        Objects.requireNonNull(id, "area_id_required");
        Objects.requireNonNull(scope, "store_scope_required");
        requireText(areaCode, "area_code_required");
        requireText(displayName, "area_display_name_required");
        requireText(status, "area_status_required");
    }

    public String reorderIntent() {
        return "area.reorder.intent";
    }

    public String domainBoundary() {
        return "Area is not a queue, customer, or reservation owner.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
