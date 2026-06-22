package com.rpb.reservation.appgate.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "platform_apps")
public class PlatformAppEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "app_key", nullable = false)
    private String appKey;

    @Column(name = "app_name", nullable = false)
    private String appName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "default_entry_route", nullable = false)
    private String defaultEntryRoute;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "config_json", columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String configJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlatformAppEntity() {
    }

    public static PlatformAppEntity of(
        UUID id,
        String appKey,
        String appName,
        String status,
        String defaultEntryRoute,
        String description,
        int sortOrder,
        String configJson
    ) {
        PlatformAppEntity entity = new PlatformAppEntity();
        OffsetDateTime now = OffsetDateTime.now();
        entity.id = id;
        entity.appKey = appKey;
        entity.appName = appName;
        entity.status = status;
        entity.defaultEntryRoute = defaultEntryRoute;
        entity.description = description;
        entity.sortOrder = sortOrder;
        entity.configJson = configJson == null ? "{}" : configJson;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public String getAppKey() { return appKey; }
    public String getAppName() { return appName; }
    public String getStatus() { return status; }
    public String getDefaultEntryRoute() { return defaultEntryRoute; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public String getConfigJson() { return configJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
