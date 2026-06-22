package com.rpb.reservation.queue.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "queue_groups")
public class QueueGroupEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "group_code", nullable = false)
    private String groupCode;

    @Column(name = "min_party_size", nullable = false)
    private Integer minPartySize;

    @Column(name = "max_party_size")
    private Integer maxPartySize;

    @Column(name = "display_i18n_key", nullable = false)
    private String displayI18nKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected QueueGroupEntity() {
    }

    public static QueueGroupEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String groupCode,
        Integer minPartySize,
        Integer maxPartySize,
        String displayI18nKey,
        String status,
        Integer sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        QueueGroupEntity entity = new QueueGroupEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.groupCode = groupCode;
        entity.minPartySize = minPartySize;
        entity.maxPartySize = maxPartySize;
        entity.displayI18nKey = displayI18nKey;
        entity.status = status;
        entity.sortOrder = sortOrder;
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
    public Integer getMinPartySize() { return minPartySize; }
    public Integer getMaxPartySize() { return maxPartySize; }
    public String getDisplayI18nKey() { return displayI18nKey; }
    public String getStatus() { return status; }
    public Integer getSortOrder() { return sortOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
