package com.rpb.reservation.audit.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * BusinessEvent domain skeleton. CheckIn is represented here with audit and
 * transition logs, not as CheckInEntity.
 */
public record BusinessEvent(
    UUID id,
    String eventType,
    String targetType,
    UUID targetId,
    String actorType,
    UUID actorId,
    String source,
    String metadata
) {

    public BusinessEvent {
        Objects.requireNonNull(id, "business_event_id_required");
        Objects.requireNonNull(targetId, "business_event_target_id_required");
        requireText(eventType, "business_event_type_required");
        requireText(targetType, "business_event_target_type_required");
        requireText(actorType, "business_event_actor_type_required");
        requireText(source, "business_event_source_required");
    }

    public BusinessEvent(UUID id, String eventType, String targetType, UUID targetId) {
        this(id, eventType, targetType, targetId, "system", null, "system", null);
    }

    public static BusinessEvent checkInEvent(String targetType, UUID targetId) {
        return new BusinessEvent(UUID.randomUUID(), "reservation_checked_in", targetType, targetId);
    }

    public String status() {
        return "event_recorded";
    }

    public String recordIntent() {
        return "business_event.record.intent";
    }

    public String domainBoundary() {
        return "BusinessEvent records CheckIn evidence and is not CheckInEntity.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
