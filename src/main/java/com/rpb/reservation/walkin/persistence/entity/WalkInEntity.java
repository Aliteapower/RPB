package com.rpb.reservation.walkin.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "walk_ins")
public class WalkInEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "walk_in_code", nullable = false)
    private String walkInCode;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "arrived_at", nullable = false)
    private OffsetDateTime arrivedAt;

    @Column(name = "status", nullable = false)
    private String status;

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

    protected WalkInEntity() {
    }

    public static WalkInEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID customerId,
        String walkInCode,
        Integer partySize,
        LocalDate businessDate,
        OffsetDateTime arrivedAt,
        String status,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        WalkInEntity entity = new WalkInEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.customerId = customerId;
        entity.walkInCode = walkInCode;
        entity.partySize = partySize;
        entity.businessDate = businessDate;
        entity.arrivedAt = arrivedAt;
        entity.status = status;
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
    public UUID getCustomerId() { return customerId; }
    public String getWalkInCode() { return walkInCode; }
    public Integer getPartySize() { return partySize; }
    public LocalDate getBusinessDate() { return businessDate; }
    public OffsetDateTime getArrivedAt() { return arrivedAt; }
    public String getStatus() { return status; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
