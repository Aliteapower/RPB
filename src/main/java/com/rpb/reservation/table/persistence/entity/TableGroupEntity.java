package com.rpb.reservation.table.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "table_groups")
public class TableGroupEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(name = "group_type", nullable = false)
    private String groupType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "capacity_min", nullable = false)
    private Integer capacityMin;

    @Column(name = "capacity_max", nullable = false)
    private Integer capacityMax;

    @Column(name = "active_from_at")
    private OffsetDateTime activeFromAt;

    @Column(name = "active_until_at")
    private OffsetDateTime activeUntilAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected TableGroupEntity() {
    }

    public static TableGroupEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String groupCode,
        String groupType,
        String status,
        String displayName,
        Integer capacityMin,
        Integer capacityMax,
        OffsetDateTime activeFromAt,
        OffsetDateTime activeUntilAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        TableGroupEntity entity = new TableGroupEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.groupCode = groupCode;
        entity.groupType = groupType;
        entity.status = status;
        entity.displayName = displayName;
        entity.capacityMin = capacityMin;
        entity.capacityMax = capacityMax;
        entity.activeFromAt = activeFromAt;
        entity.activeUntilAt = activeUntilAt;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        entity.version = version;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getGroupCode() { return groupCode; }
    public String getGroupType() { return groupType; }
    public String getStatus() { return status; }
    public String getDisplayName() { return displayName; }
    public Integer getCapacityMin() { return capacityMin; }
    public Integer getCapacityMax() { return capacityMax; }
    public OffsetDateTime getActiveFromAt() { return activeFromAt; }
    public OffsetDateTime getActiveUntilAt() { return activeUntilAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
