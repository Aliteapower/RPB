package com.rpb.reservation.appgate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.appgate.persistence.entity.AppGateAuditLogEntity;
import com.rpb.reservation.appgate.persistence.entity.StoreAppSettingEntity;
import com.rpb.reservation.appgate.persistence.entity.TenantAppEntitlementEntity;
import com.rpb.reservation.appgate.persistence.repository.AppGateAuditLogJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.StoreAppSettingJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.TenantAppEntitlementJpaRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppGateCommandServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000008101");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000008101");
    private static final UUID OPERATOR_ID = UUID.fromString("30000000-0000-0000-0000-000000008101");
    private static final String APP_KEY = "reservation_queue";
    private static final String OPERATOR_ROLE = "tenant_admin";

    private TenantAppEntitlementJpaRepository entitlements;
    private StoreAppSettingJpaRepository storeSettings;
    private AppGateAuditLogJpaRepository auditLogs;
    private AppGateCommandService service;

    @BeforeEach
    void setUp() {
        entitlements = mock(TenantAppEntitlementJpaRepository.class);
        storeSettings = mock(StoreAppSettingJpaRepository.class);
        auditLogs = mock(AppGateAuditLogJpaRepository.class);
        service = new AppGateCommandService(entitlements, storeSettings, auditLogs);
        when(entitlements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeSettings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditLogs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enableTenantAppSetsEnabledAndWritesAudit() {
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY))
            .thenReturn(Optional.of(entitlement("disabled", "{}")));

        TenantAppEntitlementEntity saved = service.enableTenantApp(TENANT_ID, APP_KEY, OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.getStatus()).isEqualTo("enabled");
        assertAudit("TENANT_APP_ENABLED", null);
    }

    @Test
    void disableTenantAppSetsDisabledAndWritesAudit() {
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY))
            .thenReturn(Optional.of(entitlement("enabled", "{}")));

        TenantAppEntitlementEntity saved = service.disableTenantApp(TENANT_ID, APP_KEY, OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.getStatus()).isEqualTo("disabled");
        assertAudit("TENANT_APP_DISABLED", null);
    }

    @Test
    void suspendTenantAppSetsSuspendedAndWritesAudit() {
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY))
            .thenReturn(Optional.of(entitlement("enabled", "{}")));

        TenantAppEntitlementEntity saved = service.suspendTenantApp(TENANT_ID, APP_KEY, OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.getStatus()).isEqualTo("suspended");
        assertAudit("TENANT_APP_SUSPENDED", null);
    }

    @Test
    void updateTenantAppConfigPersistsConfigAndWritesAudit() {
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY))
            .thenReturn(Optional.of(entitlement("enabled", "{}")));

        TenantAppEntitlementEntity saved = service.updateTenantAppConfig(TENANT_ID, APP_KEY, "{\"mode\":\"strict\"}", OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.getConfigJson()).isEqualTo("{\"mode\":\"strict\"}");
        assertAudit("TENANT_APP_UPDATED", null);
    }

    @Test
    void enableStoreAppSetsEnabledVisibleAndWritesAudit() {
        when(storeSettings.findByTenantIdAndStoreIdAndAppKey(TENANT_ID, STORE_ID, APP_KEY))
            .thenReturn(Optional.of(storeSetting(false, false, "{}")));

        StoreAppSettingEntity saved = service.enableStoreApp(TENANT_ID, STORE_ID, APP_KEY, OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.isEntryVisible()).isTrue();
        assertAudit("STORE_APP_ENABLED", STORE_ID);
    }

    @Test
    void disableStoreAppSetsDisabledAndWritesAudit() {
        when(storeSettings.findByTenantIdAndStoreIdAndAppKey(TENANT_ID, STORE_ID, APP_KEY))
            .thenReturn(Optional.of(storeSetting(true, true, "{}")));

        StoreAppSettingEntity saved = service.disableStoreApp(TENANT_ID, STORE_ID, APP_KEY, OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.isEnabled()).isFalse();
        assertAudit("STORE_APP_DISABLED", STORE_ID);
    }

    @Test
    void updateStoreVisibilityPersistsVisibilityAndWritesAudit() {
        when(storeSettings.findByTenantIdAndStoreIdAndAppKey(TENANT_ID, STORE_ID, APP_KEY))
            .thenReturn(Optional.of(storeSetting(true, true, "{}")));

        StoreAppSettingEntity saved = service.updateStoreAppVisibility(TENANT_ID, STORE_ID, APP_KEY, false, OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.isEntryVisible()).isFalse();
        assertAudit("STORE_APP_VISIBILITY_UPDATED", STORE_ID);
    }

    @Test
    void updateStoreConfigPersistsConfigAndWritesAudit() {
        when(storeSettings.findByTenantIdAndStoreIdAndAppKey(TENANT_ID, STORE_ID, APP_KEY))
            .thenReturn(Optional.of(storeSetting(true, true, "{}")));

        StoreAppSettingEntity saved = service.updateStoreAppConfig(TENANT_ID, STORE_ID, APP_KEY, "{\"entry\":\"staff\"}", OPERATOR_ID, OPERATOR_ROLE);

        assertThat(saved.getConfigJson()).isEqualTo("{\"entry\":\"staff\"}");
        assertAudit("STORE_APP_CONFIG_UPDATED", STORE_ID);
    }

    private void assertAudit(String action, UUID storeId) {
        ArgumentCaptor<AppGateAuditLogEntity> captor = ArgumentCaptor.forClass(AppGateAuditLogEntity.class);
        verify(auditLogs).save(captor.capture());
        AppGateAuditLogEntity audit = captor.getValue();
        assertThat(audit.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(audit.getStoreId()).isEqualTo(storeId);
        assertThat(audit.getAppKey()).isEqualTo(APP_KEY);
        assertThat(audit.getAction()).isEqualTo(action);
        assertThat(audit.getOperatorUserId()).isEqualTo(OPERATOR_ID);
        assertThat(audit.getOperatorRole()).isEqualTo(OPERATOR_ROLE);
        assertThat(audit.getBeforeJson()).isNotBlank();
        assertThat(audit.getAfterJson()).isNotBlank();
    }

    private static TenantAppEntitlementEntity entitlement(String status, String configJson) {
        return TenantAppEntitlementEntity.of(
            UUID.randomUUID(),
            TENANT_ID,
            APP_KEY,
            status,
            OffsetDateTime.now().minusDays(1),
            null,
            configJson,
            null,
            null
        );
    }

    private static StoreAppSettingEntity storeSetting(boolean enabled, boolean visible, String configJson) {
        return StoreAppSettingEntity.of(
            UUID.randomUUID(),
            TENANT_ID,
            STORE_ID,
            APP_KEY,
            enabled,
            visible,
            configJson,
            null,
            null,
            null
        );
    }
}
