package com.rpb.reservation.appgate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.appgate.persistence.entity.PlatformAppEntity;
import com.rpb.reservation.appgate.persistence.entity.StoreAppSettingEntity;
import com.rpb.reservation.appgate.persistence.entity.TenantAppEntitlementEntity;
import com.rpb.reservation.appgate.persistence.repository.PlatformAppJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.StoreAppSettingJpaRepository;
import com.rpb.reservation.appgate.persistence.repository.TenantAppEntitlementJpaRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppGateServiceTest {
    private static final String APP_KEY = "reservation_queue";
    private static final String PERMISSION = "reservation.create";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000008001");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000008001");
    private static final UUID OTHER_STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000008099");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000008001");

    private PlatformAppJpaRepository platformApps;
    private TenantAppEntitlementJpaRepository entitlements;
    private StoreAppSettingJpaRepository storeSettings;
    private AppGateService service;

    @BeforeEach
    void setUp() {
        platformApps = mock(PlatformAppJpaRepository.class);
        entitlements = mock(TenantAppEntitlementJpaRepository.class);
        storeSettings = mock(StoreAppSettingJpaRepository.class);
        service = new AppGateService(platformApps, entitlements, storeSettings);
        allowPlatformTenantAndStore();
    }

    @Test
    void allowsActiveAppEnabledTenantEnabledStoreAccessibleStoreAndPermission() {
        AppGateDecision decision = service.evaluate(request(actor(Set.of(STORE_ID), Set.of(PERMISSION))));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.denyReason()).isNull();
        assertThat(decision.appKey()).isEqualTo(APP_KEY);
        assertThat(decision.tenantId()).isEqualTo(TENANT_ID);
        assertThat(decision.storeId()).isEqualTo(STORE_ID);
        assertThat(decision.requiredPermission()).isEqualTo(PERMISSION);
    }

    @Test
    void inactivePlatformAppDeniesWithAppDisabled() {
        when(platformApps.findByAppKey(APP_KEY)).thenReturn(Optional.of(platformApp("disabled")));

        AppGateDecision decision = service.evaluate(request(actor(Set.of(STORE_ID), Set.of(PERMISSION))));

        assertDenied(decision, AppGateDenyReason.APP_DISABLED, "appgate.app_disabled");
    }

    @Test
    void missingTenantEntitlementDeniesWithTenantAppNotEnabled() {
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY)).thenReturn(Optional.empty());

        AppGateDecision decision = service.evaluate(request(actor(Set.of(STORE_ID), Set.of(PERMISSION))));

        assertDenied(decision, AppGateDenyReason.TENANT_APP_NOT_ENABLED, "appgate.tenant_app_not_enabled");
    }

    @Test
    void disabledTenantEntitlementDeniesWithTenantAppNotEnabled() {
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY))
            .thenReturn(Optional.of(entitlement("disabled", null)));

        AppGateDecision decision = service.evaluate(request(actor(Set.of(STORE_ID), Set.of(PERMISSION))));

        assertDenied(decision, AppGateDenyReason.TENANT_APP_NOT_ENABLED, "appgate.tenant_app_not_enabled");
    }

    @Test
    void expiredTrialDeniesWithTenantAppExpired() {
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY))
            .thenReturn(Optional.of(entitlement("trial", OffsetDateTime.now().minusMinutes(1))));

        AppGateDecision decision = service.evaluate(request(actor(Set.of(STORE_ID), Set.of(PERMISSION))));

        assertDenied(decision, AppGateDenyReason.TENANT_APP_EXPIRED, "appgate.tenant_app_expired");
    }

    @Test
    void disabledStoreSettingDeniesWithStoreAppNotEnabled() {
        when(storeSettings.findByTenantIdAndStoreIdAndAppKey(TENANT_ID, STORE_ID, APP_KEY))
            .thenReturn(Optional.of(storeSetting(false, true)));

        AppGateDecision decision = service.evaluate(request(actor(Set.of(STORE_ID), Set.of(PERMISSION))));

        assertDenied(decision, AppGateDenyReason.STORE_APP_NOT_ENABLED, "appgate.store_app_not_enabled");
    }

    @Test
    void actorWithoutStoreAccessDeniesWithStoreAccessDenied() {
        AppGateDecision decision = service.evaluate(request(actor(Set.of(OTHER_STORE_ID), Set.of(PERMISSION))));

        assertDenied(decision, AppGateDenyReason.STORE_ACCESS_DENIED, "appgate.store_access_denied");
    }

    @Test
    void actorWithoutPermissionDeniesWithPermissionDenied() {
        AppGateDecision decision = service.evaluate(request(actor(Set.of(STORE_ID), Set.of("cleaning.complete"))));

        assertDenied(decision, AppGateDenyReason.PERMISSION_DENIED, "appgate.permission_denied");
    }

    @Test
    void visibleAppsRecognizesReservationCheckInAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("reservation.check_in")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("reservation.check_in");
    }

    @Test
    void visibleAppsRecognizesReservationSeatAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("reservation.seat")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("reservation.seat");
    }

    @Test
    void visibleAppsRecognizesReservationTodayViewAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("reservation.today_view")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("reservation.today_view");
    }

    @Test
    void visibleAppsRecognizesReservationQueueAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("reservation.queue")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("reservation.queue");
    }

    @Test
    void visibleAppsRecognizesQueueCallAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("queue.call")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("queue.call");
    }

    @Test
    void visibleAppsRecognizesQueueSeatAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("queue.seat")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("queue.seat");
    }

    @Test
    void visibleAppsRecognizesQueueSkipAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("queue.skip")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("queue.skip");
    }

    @Test
    void visibleAppsRecognizesQueueRejoinAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("queue.rejoin")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("queue.rejoin");
    }

    @Test
    void visibleAppsRecognizesQueueViewAsReservationQueuePermission() {
        when(entitlements.findAllByTenantId(TENANT_ID)).thenReturn(List.of(entitlement("enabled", null)));
        when(storeSettings.findAllByTenantIdAndStoreId(TENANT_ID, STORE_ID)).thenReturn(List.of(storeSetting(true, true)));
        when(platformApps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active"))
            .thenReturn(List.of(platformApp("active")));

        List<AppGateAppEntry> apps = service.visibleApps(
            actor(Set.of(STORE_ID), Set.of("queue.view")),
            STORE_ID
        );

        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).appKey()).isEqualTo(APP_KEY);
        assertThat(apps.get(0).permissions()).containsExactly("queue.view");
    }

    private void allowPlatformTenantAndStore() {
        when(platformApps.findByAppKey(APP_KEY)).thenReturn(Optional.of(platformApp("active")));
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY))
            .thenReturn(Optional.of(entitlement("enabled", null)));
        when(storeSettings.findByTenantIdAndStoreIdAndAppKey(TENANT_ID, STORE_ID, APP_KEY))
            .thenReturn(Optional.of(storeSetting(true, true)));
    }

    private static AppGateAccessRequest request(CurrentActor actor) {
        return new AppGateAccessRequest(APP_KEY, TENANT_ID, STORE_ID, PERMISSION, actor);
    }

    private static CurrentActor actor(Set<UUID> storeIds, Set<String> permissions) {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            permissions,
            storeIds
        );
    }

    private static PlatformAppEntity platformApp(String status) {
        return PlatformAppEntity.of(UUID.randomUUID(), APP_KEY, "订位排号系统", status, "/stores/:storeId/staff", null, 10, "{}");
    }

    private static TenantAppEntitlementEntity entitlement(String status, OffsetDateTime validUntil) {
        return TenantAppEntitlementEntity.of(UUID.randomUUID(), TENANT_ID, APP_KEY, status, OffsetDateTime.now().minusDays(1), validUntil, "{}", null, null);
    }

    private static StoreAppSettingEntity storeSetting(boolean enabled, boolean visible) {
        return StoreAppSettingEntity.of(UUID.randomUUID(), TENANT_ID, STORE_ID, APP_KEY, enabled, visible, "{}", null, null, null);
    }

    private static void assertDenied(AppGateDecision decision, AppGateDenyReason reason, String messageKey) {
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denyReason()).isEqualTo(reason);
        assertThat(decision.messageKey()).isEqualTo(messageKey);
        assertThat(decision.details()).isEmpty();
    }
}
