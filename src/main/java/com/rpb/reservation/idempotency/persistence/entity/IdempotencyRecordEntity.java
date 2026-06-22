package com.rpb.reservation.idempotency.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_snapshot", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String responseSnapshot;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected IdempotencyRecordEntity() {
    }

    public static IdempotencyRecordEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String idempotencyKey,
        String source,
        String action,
        String targetType,
        UUID targetId,
        String requestHash,
        String responseSnapshot,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.idempotencyKey = idempotencyKey;
        entity.source = source;
        entity.action = action;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.requestHash = requestHash;
        entity.responseSnapshot = responseSnapshot;
        entity.status = status;
        entity.expiresAt = expiresAt;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getSource() { return source; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getRequestHash() { return requestHash; }
    public String getResponseSnapshot() { return responseSnapshot; }
    public String getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
