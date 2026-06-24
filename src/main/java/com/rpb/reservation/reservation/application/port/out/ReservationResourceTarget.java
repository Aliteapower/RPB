package com.rpb.reservation.reservation.application.port.out;

import java.util.Objects;
import java.util.UUID;

public record ReservationResourceTarget(String resourceType, UUID resourceId) {

    public ReservationResourceTarget {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resource_type_required");
        }
        Objects.requireNonNull(resourceId, "resource_id_required");
        resourceType = resourceType.trim().toLowerCase();
    }
}
