package com.rpb.reservation.audit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "state_transition_logs")
public class StateTransitionLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "transition_code", nullable = false)
    private String transitionCode;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(name = "before_state", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String afterState;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "audit_log_id")
    private UUID auditLogId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected StateTransitionLogEntity() {
    }

    public static StateTransitionLogEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String targetType,
        UUID targetId,
        String actorType,
        UUID actorId,
        String fromStatus,
        String toStatus,
        String transitionCode,
        String triggeredBy,
        String beforeState,
        String afterState,
        String reasonCode,
        String idempotencyKey,
        String metadata,
        OffsetDateTime occurredAt,
        UUID auditLogId,
        OffsetDateTime createdAt
    ) {
        StateTransitionLogEntity entity = new StateTransitionLogEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.actorType = actorType;
        entity.actorId = actorId;
        entity.fromStatus = fromStatus;
        entity.toStatus = toStatus;
        entity.transitionCode = transitionCode;
        entity.triggeredBy = triggeredBy;
        entity.beforeState = beforeState;
        entity.afterState = afterState;
        entity.reasonCode = reasonCode;
        entity.idempotencyKey = idempotencyKey;
        entity.metadata = metadata;
        entity.occurredAt = occurredAt;
        entity.auditLogId = auditLogId;
        entity.createdAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getTransitionCode() { return transitionCode; }
    public String getTriggeredBy() { return triggeredBy; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getReasonCode() { return reasonCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMetadata() { return metadata; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public UUID getAuditLogId() { return auditLogId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
