package com.rpb.reservation.audit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "operation_code", nullable = false)
    private String operationCode;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "source", nullable = false)
    private String source;

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

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuditLogEntity() {
    }

    public static AuditLogEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String operationCode,
        String targetType,
        UUID targetId,
        String actorType,
        UUID actorId,
        String actorRole,
        String source,
        String beforeState,
        String afterState,
        String reasonCode,
        String idempotencyKey,
        String failureReason,
        String metadata,
        OffsetDateTime occurredAt,
        OffsetDateTime createdAt
    ) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.operationCode = operationCode;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.actorType = actorType;
        entity.actorId = actorId;
        entity.actorRole = actorRole;
        entity.source = source;
        entity.beforeState = beforeState;
        entity.afterState = afterState;
        entity.reasonCode = reasonCode;
        entity.idempotencyKey = idempotencyKey;
        entity.failureReason = failureReason;
        entity.metadata = metadata;
        entity.occurredAt = occurredAt;
        entity.createdAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getOperationCode() { return operationCode; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getSource() { return source; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getReasonCode() { return reasonCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFailureReason() { return failureReason; }
    public String getMetadata() { return metadata; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
