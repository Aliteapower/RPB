package com.rpb.reservation.reservation.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservation_preassignments")
public class ReservationPreassignmentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "table_id")
    private UUID tableId;

    @Column(name = "table_group_id")
    private UUID tableGroupId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "preassigned_at", nullable = false)
    private OffsetDateTime preassignedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected ReservationPreassignmentEntity() {
    }

    public static ReservationPreassignmentEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID reservationId,
        String resourceType,
        UUID tableId,
        UUID tableGroupId,
        String status,
        OffsetDateTime preassignedAt,
        OffsetDateTime releasedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt
    ) {
        ReservationPreassignmentEntity entity = new ReservationPreassignmentEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.reservationId = reservationId;
        entity.resourceType = resourceType;
        entity.tableId = tableId;
        entity.tableGroupId = tableGroupId;
        entity.status = status;
        entity.preassignedAt = preassignedAt;
        entity.releasedAt = releasedAt;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public UUID getReservationId() { return reservationId; }
    public String getResourceType() { return resourceType; }
    public UUID getTableId() { return tableId; }
    public UUID getTableGroupId() { return tableGroupId; }
    public String getStatus() { return status; }
    public OffsetDateTime getPreassignedAt() { return preassignedAt; }
    public OffsetDateTime getReleasedAt() { return releasedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
}
