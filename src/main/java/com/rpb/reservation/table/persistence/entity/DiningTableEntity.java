package com.rpb.reservation.table.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dining_tables")
public class DiningTableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "table_code", nullable = false)
    private String tableCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "capacity_min", nullable = false)
    private Integer capacityMin;

    @Column(name = "capacity_max", nullable = false)
    private Integer capacityMax;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_combinable", nullable = false)
    private Boolean combinable;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected DiningTableEntity() {
    }

    public static DiningTableEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID areaId,
        String tableCode,
        String displayName,
        Integer capacityMin,
        Integer capacityMax,
        String status,
        Integer sortOrder,
        Boolean combinable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        DiningTableEntity entity = new DiningTableEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.areaId = areaId;
        entity.tableCode = tableCode;
        entity.displayName = displayName;
        entity.capacityMin = capacityMin;
        entity.capacityMax = capacityMax;
        entity.status = status;
        entity.sortOrder = sortOrder;
        entity.combinable = combinable;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        entity.version = version;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public UUID getAreaId() { return areaId; }
    public String getTableCode() { return tableCode; }
    public String getDisplayName() { return displayName; }
    public Integer getCapacityMin() { return capacityMin; }
    public Integer getCapacityMax() { return capacityMax; }
    public String getStatus() { return status; }
    public Integer getSortOrder() { return sortOrder; }
    public Boolean getCombinable() { return combinable; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
