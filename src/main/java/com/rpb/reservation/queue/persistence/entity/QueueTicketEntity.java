package com.rpb.reservation.queue.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "queue_tickets")
public class QueueTicketEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "queue_group_id", nullable = false)
    private UUID queueGroupId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "walk_in_id")
    private UUID walkInId;

    @Column(name = "ticket_number", nullable = false)
    private Integer ticketNumber;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "called_at")
    private OffsetDateTime calledAt;

    @Column(name = "skipped_at")
    private OffsetDateTime skippedAt;

    @Column(name = "rejoined_at")
    private OffsetDateTime rejoinedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "cancellation_reason_code")
    private String cancellationReasonCode;

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

    protected QueueTicketEntity() {
    }

    public static QueueTicketEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID queueGroupId,
        UUID customerId,
        UUID reservationId,
        UUID walkInId,
        Integer ticketNumber,
        Integer partySize,
        LocalDate businessDate,
        String status,
        Integer queuePosition,
        OffsetDateTime calledAt,
        OffsetDateTime skippedAt,
        OffsetDateTime rejoinedAt,
        OffsetDateTime expiresAt,
        String cancellationReasonCode,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        QueueTicketEntity entity = new QueueTicketEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.queueGroupId = queueGroupId;
        entity.customerId = customerId;
        entity.reservationId = reservationId;
        entity.walkInId = walkInId;
        entity.ticketNumber = ticketNumber;
        entity.partySize = partySize;
        entity.businessDate = businessDate;
        entity.status = status;
        entity.queuePosition = queuePosition;
        entity.calledAt = calledAt;
        entity.skippedAt = skippedAt;
        entity.rejoinedAt = rejoinedAt;
        entity.expiresAt = expiresAt;
        entity.cancellationReasonCode = cancellationReasonCode;
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
    public UUID getQueueGroupId() { return queueGroupId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getWalkInId() { return walkInId; }
    public Integer getTicketNumber() { return ticketNumber; }
    public Integer getPartySize() { return partySize; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getStatus() { return status; }
    public Integer getQueuePosition() { return queuePosition; }
    public OffsetDateTime getCalledAt() { return calledAt; }
    public OffsetDateTime getSkippedAt() { return skippedAt; }
    public OffsetDateTime getRejoinedAt() { return rejoinedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public String getCancellationReasonCode() { return cancellationReasonCode; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
