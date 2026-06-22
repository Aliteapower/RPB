package com.rpb.reservation.store.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stores")
public class StoreEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_code", nullable = false)
    private String storeCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "locale", nullable = false)
    private String locale;

    @Column(name = "date_format", nullable = false)
    private String dateFormat;

    @Column(name = "time_format", nullable = false)
    private String timeFormat;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected StoreEntity() {
    }

    public static StoreEntity of(
        UUID id,
        UUID tenantId,
        String storeCode,
        String displayName,
        String status,
        String timezone,
        String locale,
        String dateFormat,
        String timeFormat,
        String currency,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        Integer version
    ) {
        StoreEntity entity = new StoreEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeCode = storeCode;
        entity.displayName = displayName;
        entity.status = status;
        entity.timezone = timezone;
        entity.locale = locale;
        entity.dateFormat = dateFormat;
        entity.timeFormat = timeFormat;
        entity.currency = currency;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.deletedAt = deletedAt;
        entity.version = version;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public Integer getVersion() {
        return version;
    }
}
