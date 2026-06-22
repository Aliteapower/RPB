package com.rpb.reservation.table.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "table_locks")
public class TableLockEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "lock_key", nullable = false)
    private String lockKey;

    @Column(name = "lock_owner", nullable = false)
    private String lockOwner;

    @Column(name = "locked_until_at", nullable = false)
    private OffsetDateTime lockedUntilAt;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "locked_at", nullable = false)
    private OffsetDateTime lockedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected TableLockEntity() {
    }

    public static TableLockEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String resourceType,
        UUID resourceId,
        String lockKey,
        String lockOwner,
        OffsetDateTime lockedUntilAt,
        String sourceType,
        UUID sourceId,
        String idempotencyKey,
        String status,
        OffsetDateTime lockedAt,
        OffsetDateTime releasedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer version
    ) {
        TableLockEntity entity = new TableLockEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.resourceType = resourceType;
        entity.resourceId = resourceId;
        entity.lockKey = lockKey;
        entity.lockOwner = lockOwner;
        entity.lockedUntilAt = lockedUntilAt;
        entity.sourceType = sourceType;
        entity.sourceId = sourceId;
        entity.idempotencyKey = idempotencyKey;
        entity.status = status;
        entity.lockedAt = lockedAt;
        entity.releasedAt = releasedAt;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.version = version;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getLockKey() { return lockKey; }
    public String getLockOwner() { return lockOwner; }
    public OffsetDateTime getLockedUntilAt() { return lockedUntilAt; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}
