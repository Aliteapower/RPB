package com.rpb.reservation.appgate.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "tenant_app_entitlements")
public class TenantAppEntitlementEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "app_key", nullable = false)
    private String appKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "config_json", columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String configJson;

    @Column(name = "enabled_by")
    private UUID enabledBy;

    @Column(name = "enabled_at")
    private OffsetDateTime enabledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantAppEntitlementEntity() {
    }

    public static TenantAppEntitlementEntity of(
        UUID id,
        UUID tenantId,
        String appKey,
        String status,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String configJson,
        UUID enabledBy,
        OffsetDateTime enabledAt
    ) {
        TenantAppEntitlementEntity entity = new TenantAppEntitlementEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.appKey = appKey;
        entity.status = status;
        entity.validFrom = validFrom;
        entity.validUntil = validUntil;
        entity.configJson = configJson == null ? "{}" : configJson;
        entity.enabledBy = enabledBy;
        entity.enabledAt = enabledAt;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void updateStatus(String status, UUID operatorId, OffsetDateTime now) {
        this.status = status;
        this.enabledBy = operatorId;
        this.enabledAt = ("enabled".equals(status) || "trial".equals(status)) ? now : this.enabledAt;
        this.updatedAt = now;
    }

    public void updateConfig(String configJson, OffsetDateTime now) {
        this.configJson = configJson == null ? "{}" : configJson;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAppKey() { return appKey; }
    public String getStatus() { return status; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public OffsetDateTime getValidUntil() { return validUntil; }
    public String getConfigJson() { return configJson; }
    public UUID getEnabledBy() { return enabledBy; }
    public OffsetDateTime getEnabledAt() { return enabledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
