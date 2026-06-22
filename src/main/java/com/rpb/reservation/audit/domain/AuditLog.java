package com.rpb.reservation.audit.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * AuditLog domain skeleton. It records critical operation evidence and is not
 * a command handler.
 */
public record AuditLog(
    UUID id,
    String operationCode,
    String targetType,
    UUID targetId,
    String source,
    String actorType,
    UUID actorId,
    String metadata
) {

    public AuditLog {
        Objects.requireNonNull(id, "audit_log_id_required");
        requireText(operationCode, "audit_operation_code_required");
        requireText(targetType, "audit_target_type_required");
        requireText(source, "audit_source_required");
        requireText(actorType, "audit_actor_type_required");
    }

    public AuditLog(UUID id, String operationCode, String targetType, UUID targetId, String source) {
        this(id, operationCode, targetType, targetId, source, "system", null, null);
    }

    public String status() {
        return "audit_recorded";
    }

    public String recordFailureIntent() {
        return "audit_log.record_failure.intent";
    }

    public String domainBoundary() {
        return "AuditLog records evidence and is not business mutation logic.";
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
