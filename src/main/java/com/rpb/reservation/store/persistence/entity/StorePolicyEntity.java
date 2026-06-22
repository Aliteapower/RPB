package com.rpb.reservation.store.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "store_policies")
public class StorePolicyEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "reservation_hold_minutes", nullable = false)
    private Integer reservationHoldMinutes;

    @Column(name = "queue_call_hold_minutes", nullable = false)
    private Integer queueCallHoldMinutes;

    @Column(name = "expected_dining_minutes", nullable = false)
    private Integer expectedDiningMinutes;

    @Column(name = "queue_rejoin_policy_code", nullable = false)
    private String queueRejoinPolicyCode;

    @Column(name = "table_assignment_policy_code", nullable = false)
    private String tableAssignmentPolicyCode;

    @Column(name = "effective_from_at", nullable = false)
    private OffsetDateTime effectiveFromAt;

    @Column(name = "effective_to_at")
    private OffsetDateTime effectiveToAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected StorePolicyEntity() {
    }

    public static StorePolicyEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        Integer reservationHoldMinutes,
        Integer queueCallHoldMinutes,
        Integer expectedDiningMinutes,
        String queueRejoinPolicyCode,
        String tableAssignmentPolicyCode,
        OffsetDateTime effectiveFromAt,
        OffsetDateTime effectiveToAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        StorePolicyEntity entity = new StorePolicyEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.reservationHoldMinutes = reservationHoldMinutes;
        entity.queueCallHoldMinutes = queueCallHoldMinutes;
        entity.expectedDiningMinutes = expectedDiningMinutes;
        entity.queueRejoinPolicyCode = queueRejoinPolicyCode;
        entity.tableAssignmentPolicyCode = tableAssignmentPolicyCode;
        entity.effectiveFromAt = effectiveFromAt;
        entity.effectiveToAt = effectiveToAt;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        entity.version = version;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public Integer getReservationHoldMinutes() { return reservationHoldMinutes; }
    public Integer getQueueCallHoldMinutes() { return queueCallHoldMinutes; }
    public Integer getExpectedDiningMinutes() { return expectedDiningMinutes; }
    public String getQueueRejoinPolicyCode() { return queueRejoinPolicyCode; }
    public String getTableAssignmentPolicyCode() { return tableAssignmentPolicyCode; }
    public OffsetDateTime getEffectiveFromAt() { return effectiveFromAt; }
    public OffsetDateTime getEffectiveToAt() { return effectiveToAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
