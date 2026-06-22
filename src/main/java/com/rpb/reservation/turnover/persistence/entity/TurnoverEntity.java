package com.rpb.reservation.turnover.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "turnovers")
public class TurnoverEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "seating_id", nullable = false)
    private UUID seatingId;

    @Column(name = "cleaning_id")
    private UUID cleaningId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "seated_at", nullable = false)
    private OffsetDateTime seatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cleaning_completed_at")
    private OffsetDateTime cleaningCompletedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected TurnoverEntity() {
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public UUID getSeatingId() { return seatingId; }
    public UUID getCleaningId() { return cleaningId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public OffsetDateTime getSeatedAt() { return seatedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public OffsetDateTime getCleaningCompletedAt() { return cleaningCompletedAt; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
}
