package com.rpb.reservation.appgate.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "app_gate_audit_logs")
public class AppGateAuditLogEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "app_key", nullable = false)
    private String appKey;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "operator_user_id")
    private UUID operatorUserId;

    @Column(name = "operator_role")
    private String operatorRole;

    @Column(name = "before_json", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String afterJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AppGateAuditLogEntity() {
    }

    public static AppGateAuditLogEntity of(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String appKey,
        String action,
        UUID operatorUserId,
        String operatorRole,
        String beforeJson,
        String afterJson,
        OffsetDateTime createdAt
    ) {
        AppGateAuditLogEntity entity = new AppGateAuditLogEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.storeId = storeId;
        entity.appKey = appKey;
        entity.action = action;
        entity.operatorUserId = operatorUserId;
        entity.operatorRole = operatorRole;
        entity.beforeJson = beforeJson;
        entity.afterJson = afterJson;
        entity.createdAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStoreId() { return storeId; }
    public String getAppKey() { return appKey; }
    public String getAction() { return action; }
    public UUID getOperatorUserId() { return operatorUserId; }
    public String getOperatorRole() { return operatorRole; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
