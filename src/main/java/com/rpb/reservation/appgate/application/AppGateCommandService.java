package com.rpb.reservation.appgate.application;

import com.rpb.reservation.appgate.persistence.entity.AppGateAuditLogEntity;
import com.rpb.reservation.appgate.persistence.entity.StoreAppSettingEntity;
import com.rpb.reservation.appgate.persistence.entity.TenantAppEntitlementEntity;
import com.rpb.reservation.appgate.persistence.repository.AppGateAuditLogJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.StoreAppSettingJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.TenantAppEntitlementJpaRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppGateCommandService {
    private final TenantAppEntitlementJpaRepository entitlements;
    private final StoreAppSettingJpaRepository storeSettings;
    private final AppGateAuditLogJpaRepository auditLogs;

    public AppGateCommandService(
        TenantAppEntitlementJpaRepository entitlements,
        StoreAppSettingJpaRepository storeSettings,
        AppGateAuditLogJpaRepository auditLogs
    ) {
        this.entitlements = entitlements;
        this.storeSettings = storeSettings;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public TenantAppEntitlementEntity enableTenantApp(UUID tenantId, String appKey, UUID operatorId, String operatorRole) {
        OffsetDateTime now = OffsetDateTime.now();
        TenantAppEntitlementEntity entitlement = findOrCreateTenantApp(tenantId, appKey, "disabled", now);
        String before = tenantSnapshot(entitlement);
        entitlement.updateStatus("enabled", operatorId, now);
        TenantAppEntitlementEntity saved = entitlements.save(entitlement);
        appendAudit(tenantId, null, appKey, "TENANT_APP_ENABLED", operatorId, operatorRole, before, tenantSnapshot(saved), now);
        return saved;
    }

    @Transactional
    public TenantAppEntitlementEntity disableTenantApp(UUID tenantId, String appKey, UUID operatorId, String operatorRole) {
        OffsetDateTime now = OffsetDateTime.now();
        TenantAppEntitlementEntity entitlement = findOrCreateTenantApp(tenantId, appKey, "enabled", now);
        String before = tenantSnapshot(entitlement);
        entitlement.updateStatus("disabled", operatorId, now);
        TenantAppEntitlementEntity saved = entitlements.save(entitlement);
        appendAudit(tenantId, null, appKey, "TENANT_APP_DISABLED", operatorId, operatorRole, before, tenantSnapshot(saved), now);
        return saved;
    }

    @Transactional
    public TenantAppEntitlementEntity suspendTenantApp(UUID tenantId, String appKey, UUID operatorId, String operatorRole) {
        OffsetDateTime now = OffsetDateTime.now();
        TenantAppEntitlementEntity entitlement = findOrCreateTenantApp(tenantId, appKey, "enabled", now);
        String before = tenantSnapshot(entitlement);
        entitlement.updateStatus("suspended", operatorId, now);
        TenantAppEntitlementEntity saved = entitlements.save(entitlement);
        appendAudit(tenantId, null, appKey, "TENANT_APP_SUSPENDED", operatorId, operatorRole, before, tenantSnapshot(saved), now);
        return saved;
    }

    @Transactional
    public TenantAppEntitlementEntity updateTenantAppConfig(
        UUID tenantId,
        String appKey,
        String configJson,
        UUID operatorId,
        String operatorRole
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        TenantAppEntitlementEntity entitlement = findOrCreateTenantApp(tenantId, appKey, "enabled", now);
        String before = tenantSnapshot(entitlement);
        entitlement.updateConfig(configJson, now);
        TenantAppEntitlementEntity saved = entitlements.save(entitlement);
        appendAudit(tenantId, null, appKey, "TENANT_APP_UPDATED", operatorId, operatorRole, before, tenantSnapshot(saved), now);
        return saved;
    }

    @Transactional
    public StoreAppSettingEntity enableStoreApp(UUID tenantId, UUID storeId, String appKey, UUID operatorId, String operatorRole) {
        OffsetDateTime now = OffsetDateTime.now();
        StoreAppSettingEntity setting = findOrCreateStoreApp(tenantId, storeId, appKey, false, false);
        String before = storeSnapshot(setting);
        setting.enable(operatorId, now);
        StoreAppSettingEntity saved = storeSettings.save(setting);
        appendAudit(tenantId, storeId, appKey, "STORE_APP_ENABLED", operatorId, operatorRole, before, storeSnapshot(saved), now);
        return saved;
    }

    @Transactional
    public StoreAppSettingEntity disableStoreApp(UUID tenantId, UUID storeId, String appKey, UUID operatorId, String operatorRole) {
        OffsetDateTime now = OffsetDateTime.now();
        StoreAppSettingEntity setting = findOrCreateStoreApp(tenantId, storeId, appKey, true, true);
        String before = storeSnapshot(setting);
        setting.disable(now);
        StoreAppSettingEntity saved = storeSettings.save(setting);
        appendAudit(tenantId, storeId, appKey, "STORE_APP_DISABLED", operatorId, operatorRole, before, storeSnapshot(saved), now);
        return saved;
    }

    @Transactional
    public StoreAppSettingEntity updateStoreAppVisibility(
        UUID tenantId,
        UUID storeId,
        String appKey,
        boolean visible,
        UUID operatorId,
        String operatorRole
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        StoreAppSettingEntity setting = findOrCreateStoreApp(tenantId, storeId, appKey, true, true);
        String before = storeSnapshot(setting);
        setting.updateVisibility(visible, now);
        StoreAppSettingEntity saved = storeSettings.save(setting);
        appendAudit(tenantId, storeId, appKey, "STORE_APP_VISIBILITY_UPDATED", operatorId, operatorRole, before, storeSnapshot(saved), now);
        return saved;
    }

    @Transactional
    public StoreAppSettingEntity updateStoreAppConfig(
        UUID tenantId,
        UUID storeId,
        String appKey,
        String configJson,
        UUID operatorId,
        String operatorRole
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        StoreAppSettingEntity setting = findOrCreateStoreApp(tenantId, storeId, appKey, true, true);
        String before = storeSnapshot(setting);
        setting.updateConfig(configJson, now);
        StoreAppSettingEntity saved = storeSettings.save(setting);
        appendAudit(tenantId, storeId, appKey, "STORE_APP_CONFIG_UPDATED", operatorId, operatorRole, before, storeSnapshot(saved), now);
        return saved;
    }

    private TenantAppEntitlementEntity findOrCreateTenantApp(UUID tenantId, String appKey, String defaultStatus, OffsetDateTime now) {
        return entitlements.findByTenantIdAndAppKey(tenantId, appKey)
            .orElseGet(() -> TenantAppEntitlementEntity.of(UUID.randomUUID(), tenantId, appKey, defaultStatus, now, null, "{}", null, null));
    }

    private StoreAppSettingEntity findOrCreateStoreApp(
        UUID tenantId,
        UUID storeId,
        String appKey,
        boolean enabled,
        boolean visible
    ) {
        return storeSettings.findByTenantIdAndStoreIdAndAppKey(tenantId, storeId, appKey)
            .orElseGet(() -> StoreAppSettingEntity.of(UUID.randomUUID(), tenantId, storeId, appKey, enabled, visible, "{}", null, null, null));
    }

    private void appendAudit(
        UUID tenantId,
        UUID storeId,
        String appKey,
        String action,
        UUID operatorId,
        String operatorRole,
        String before,
        String after,
        OffsetDateTime now
    ) {
        auditLogs.save(AppGateAuditLogEntity.of(
            UUID.randomUUID(),
            tenantId,
            storeId,
            appKey,
            action,
            operatorId,
            operatorRole,
            before,
            after,
            now
        ));
    }

    private static String tenantSnapshot(TenantAppEntitlementEntity entitlement) {
        return """
            {"status":"%s","configJson":%s}
            """.formatted(entitlement.getStatus(), config(entitlement.getConfigJson())).trim();
    }

    private static String storeSnapshot(StoreAppSettingEntity setting) {
        return """
            {"isEnabled":%s,"entryVisible":%s,"configJson":%s}
            """.formatted(setting.isEnabled(), setting.isEntryVisible(), config(setting.getConfigJson())).trim();
    }

    private static String config(String configJson) {
        return configJson == null || configJson.isBlank() ? "{}" : configJson;
    }
}
