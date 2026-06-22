package com.rpb.reservation.cleaning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cleanings")
public class CleaningEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "seating_id", nullable = false)
    private UUID seatingId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "table_group_id")
    private UUID tableGroupId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected CleaningEntity() {
    }

    public static CleaningEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID seatingId,
        String resourceType,
        UUID tableId,
        UUID tableGroupId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime releasedAt,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        CleaningEntity entity = new CleaningEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.seatingId = seatingId;
        entity.resourceType = resourceType;
        entity.tableId = tableId;
        entity.tableGroupId = tableGroupId;
        entity.status = status;
        entity.startedAt = startedAt;
        entity.completedAt = completedAt;
        entity.releasedAt = releasedAt;
        entity.note = note;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        entity.version = version;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public UUID getSeatingId() { return seatingId; }
    public String getResourceType() { return resourceType; }
    public UUID getTableId() { return tableId; }
    public UUID getTableGroupId() { return tableGroupId; }
    public String getStatus() { return status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
