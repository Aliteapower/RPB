package com.rpb.reservation.seating.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seating_resources")
public class SeatingResourceEntity {

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

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected SeatingResourceEntity() {
    }

    public static SeatingResourceEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID seatingId,
        String resourceType,
        UUID tableId,
        UUID tableGroupId,
        OffsetDateTime assignedAt,
        OffsetDateTime releasedAt,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt
    ) {
        SeatingResourceEntity entity = new SeatingResourceEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.seatingId = seatingId;
        entity.resourceType = resourceType;
        entity.tableId = tableId;
        entity.tableGroupId = tableGroupId;
        entity.assignedAt = assignedAt;
        entity.releasedAt = releasedAt;
        entity.status = status;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public UUID getSeatingId() { return seatingId; }
    public String getResourceType() { return resourceType; }
    public UUID getTableId() { return tableId; }
    public UUID getTableGroupId() { return tableGroupId; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
}
