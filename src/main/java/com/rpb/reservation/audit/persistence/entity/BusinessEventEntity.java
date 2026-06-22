package com.rpb.reservation.audit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "business_events")
public class BusinessEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

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

    @Column(name = "metadata", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected BusinessEventEntity() {
    }

    public static BusinessEventEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String eventType,
        String targetType,
        UUID targetId,
        String actorType,
        UUID actorId,
        String source,
        String beforeState,
        String afterState,
        String reasonCode,
        String idempotencyKey,
        String metadata,
        OffsetDateTime occurredAt,
        OffsetDateTime createdAt
    ) {
        BusinessEventEntity entity = new BusinessEventEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.eventType = eventType;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.actorType = actorType;
        entity.actorId = actorId;
        entity.source = source;
        entity.beforeState = beforeState;
        entity.afterState = afterState;
        entity.reasonCode = reasonCode;
        entity.idempotencyKey = idempotencyKey;
        entity.metadata = metadata;
        entity.occurredAt = occurredAt;
        entity.createdAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getEventType() { return eventType; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getSource() { return source; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getReasonCode() { return reasonCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMetadata() { return metadata; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
