package com.rpb.reservation.audit.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * StateTransitionLog domain skeleton. It records state movement evidence but
 * does not decide whether the transition is legal.
 */
public record StateTransitionLog(
    UUID id,
    String targetType,
    UUID targetId,
    String fromStatus,
    String toStatus,
    String transitionCode,
    String actorType,
    UUID actorId,
    String triggeredBy,
    String metadata
) {

    public StateTransitionLog {
        Objects.requireNonNull(id, "state_transition_log_id_required");
        Objects.requireNonNull(targetId, "state_transition_target_id_required");
        requireText(targetType, "state_transition_target_type_required");
        requireText(toStatus, "state_transition_to_status_required");
        requireText(transitionCode, "state_transition_code_required");
        requireText(actorType, "state_transition_actor_type_required");
        requireText(triggeredBy, "state_transition_triggered_by_required");
    }

    public StateTransitionLog(UUID id, String targetType, UUID targetId, String fromStatus, String toStatus, String transitionCode) {
        this(id, targetType, targetId, fromStatus, toStatus, transitionCode, "system", null, "system", null);
    }

    public String status() {
        return toStatus;
    }

    public String recordIntent() {
        return "state_transition_log.record.intent";
    }

    public String domainBoundary() {
        return "StateTransitionLog is history evidence, not current state owner.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
