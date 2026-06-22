package com.rpb.reservation.table.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "table_group_members")
public class TableGroupMemberEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "table_group_id", nullable = false)
    private UUID tableGroupId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "member_role")
    private String memberRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected TableGroupMemberEntity() {
    }

    public static TableGroupMemberEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID tableGroupId,
        UUID tableId,
        String memberRole,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt
    ) {
        TableGroupMemberEntity entity = new TableGroupMemberEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.tableGroupId = tableGroupId;
        entity.tableId = tableId;
        entity.memberRole = memberRole;
        entity.createdAt = createdAt;
        entity.deletedAt = deletedAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public UUID getTableGroupId() { return tableGroupId; }
    public UUID getTableId() { return tableId; }
    public String getMemberRole() { return memberRole; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
}
