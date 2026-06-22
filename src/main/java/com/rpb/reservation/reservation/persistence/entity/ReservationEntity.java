package com.rpb.reservation.reservation.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class ReservationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "reservation_code", nullable = false)
    private String reservationCode;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "reserved_start_at", nullable = false)
    private OffsetDateTime reservedStartAt;

    @Column(name = "reserved_end_at", nullable = false)
    private OffsetDateTime reservedEndAt;

    @Column(name = "hold_until_at", nullable = false)
    private OffsetDateTime holdUntilAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "source_channel", nullable = false)
    private String sourceChannel;

    @Column(name = "cancellation_reason_code")
    private String cancellationReasonCode;

    @Column(name = "no_show_reason_code")
    private String noShowReasonCode;

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

    protected ReservationEntity() {
    }

    public static ReservationEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        UUID customerId,
        String reservationCode,
        Integer partySize,
        LocalDate businessDate,
        OffsetDateTime reservedStartAt,
        OffsetDateTime reservedEndAt,
        OffsetDateTime holdUntilAt,
        String status,
        String sourceChannel,
        String cancellationReasonCode,
        String noShowReasonCode,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        ReservationEntity entity = new ReservationEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.customerId = customerId;
        entity.reservationCode = reservationCode;
        entity.partySize = partySize;
        entity.businessDate = businessDate;
        entity.reservedStartAt = reservedStartAt;
        entity.reservedEndAt = reservedEndAt;
        entity.holdUntilAt = holdUntilAt;
        entity.status = status;
        entity.sourceChannel = sourceChannel;
        entity.cancellationReasonCode = cancellationReasonCode;
        entity.noShowReasonCode = noShowReasonCode;
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
    public String getReservationCode() { return reservationCode; }
    public Integer getPartySize() { return partySize; }
    public LocalDate getBusinessDate() { return businessDate; }
    public OffsetDateTime getReservedStartAt() { return reservedStartAt; }
    public OffsetDateTime getReservedEndAt() { return reservedEndAt; }
    public OffsetDateTime getHoldUntilAt() { return holdUntilAt; }
    public String getStatus() { return status; }
    public String getSourceChannel() { return sourceChannel; }
    public String getCancellationReasonCode() { return cancellationReasonCode; }
    public String getNoShowReasonCode() { return noShowReasonCode; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public Integer getVersion() { return version; }
}
