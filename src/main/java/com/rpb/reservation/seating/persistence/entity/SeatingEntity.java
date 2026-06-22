package com.rpb.reservation.seating.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seatings")
public class SeatingEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "queue_ticket_id")
    private UUID queueTicketId;

    @Column(name = "walk_in_id")
    private UUID walkInId;

    @Column(name = "seating_code", nullable = false)
    private String seatingCode;

    @Column(name = "party_size_snapshot", nullable = false)
    private Integer partySizeSnapshot;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "seated_at")
    private OffsetDateTime seatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "manual_override_reason_code")
    private String manualOverrideReasonCode;

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

    protected SeatingEntity() {
    }

    public static SeatingEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID reservationId,
        UUID queueTicketId,
        UUID walkInId,
        String seatingCode,
        Integer partySizeSnapshot,
        String status,
        OffsetDateTime seatedAt,
        OffsetDateTime completedAt,
        String manualOverrideReasonCode,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        SeatingEntity entity = new SeatingEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.reservationId = reservationId;
        entity.queueTicketId = queueTicketId;
        entity.walkInId = walkInId;
        entity.seatingCode = seatingCode;
        entity.partySizeSnapshot = partySizeSnapshot;
        entity.status = status;
        entity.seatedAt = seatedAt;
        entity.completedAt = completedAt;
        entity.manualOverrideReasonCode = manualOverrideReasonCode;
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
    public UUID getReservationId() { return reservationId; }
    public UUID getQueueTicketId() { return queueTicketId; }
    public UUID getWalkInId() { return walkInId; }
    public String getSeatingCode() { return seatingCode; }
    public Integer getPartySizeSnapshot() { return partySizeSnapshot; }
    public String getStatus() { return status; }
    public OffsetDateTime getSeatedAt() { return seatedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getManualOverrideReasonCode() { return manualOverrideReasonCode; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
