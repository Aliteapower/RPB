package com.rpb.reservation.appgate.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "store_app_settings")
public class StoreAppSettingEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "app_key", nullable = false)
    private String appKey;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "entry_visible", nullable = false)
    private boolean entryVisible;

    @Column(name = "config_json", columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String configJson;

    @Column(name = "enabled_by")
    private UUID enabledBy;

    @Column(name = "enabled_at")
    private OffsetDateTime enabledAt;

    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StoreAppSettingEntity() {
    }

    public static StoreAppSettingEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String appKey,
        boolean enabled,
        boolean entryVisible,
        String configJson,
        UUID enabledBy,
        OffsetDateTime enabledAt,
        OffsetDateTime disabledAt
    ) {
        StoreAppSettingEntity entity = new StoreAppSettingEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.appKey = appKey;
        entity.enabled = enabled;
        entity.entryVisible = entryVisible;
        entity.configJson = configJson == null ? "{}" : configJson;
        entity.enabledBy = enabledBy;
        entity.enabledAt = enabledAt;
        entity.disabledAt = disabledAt;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void enable(UUID operatorId, OffsetDateTime now) {
        this.enabled = true;
        this.entryVisible = true;
        this.enabledBy = operatorId;
        this.enabledAt = now;
        this.disabledAt = null;
        this.updatedAt = now;
    }

    public void disable(OffsetDateTime now) {
        this.enabled = false;
        this.disabledAt = now;
        this.updatedAt = now;
    }

    public void updateVisibility(boolean entryVisible, OffsetDateTime now) {
        this.entryVisible = entryVisible;
        this.updatedAt = now;
    }

    public void updateConfig(String configJson, OffsetDateTime now) {
        this.configJson = configJson == null ? "{}" : configJson;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getAppKey() { return appKey; }
    public boolean isEnabled() { return enabled; }
    public boolean isEntryVisible() { return entryVisible; }
    public String getConfigJson() { return configJson; }
    public UUID getEnabledBy() { return enabledBy; }
    public OffsetDateTime getEnabledAt() { return enabledAt; }
    public OffsetDateTime getDisabledAt() { return disabledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
