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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppGateEnabledEntitlementExpiryTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009401");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000009401");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000009401");
    private static final String APP_KEY = "reservation_queue";

    @Test
    void enabledEntitlementWithPastValidUntilIsRejectedByAppGate() {
        PlatformAppJpaRepository platformApps = mock(PlatformAppJpaRepository.class);
        TenantAppEntitlementJpaRepository entitlements = mock(TenantAppEntitlementJpaRepository.class);
        StoreAppSettingJpaRepository storeSettings = mock(StoreAppSettingJpaRepository.class);
        AppGateService service = new AppGateService(platformApps, entitlements, storeSettings);

        when(platformApps.findByAppKey(APP_KEY)).thenReturn(Optional.of(PlatformAppEntity.of(
            UUID.randomUUID(),
            APP_KEY,
            "预约排队叫号产线",
            "active",
            "/stores/:storeId/staff",
            "预约、排队、叫号一体化产线",
            10,
            "{}"
        )));
        when(entitlements.findByTenantIdAndAppKey(TENANT_ID, APP_KEY)).thenReturn(Optional.of(TenantAppEntitlementEntity.of(
            UUID.randomUUID(),
            TENANT_ID,
            APP_KEY,
            "enabled",
            OffsetDateTime.now().minusMonths(2),
            OffsetDateTime.now().minusDays(1),
            "{}",
            null,
            null
        )));
        when(storeSettings.findByTenantIdAndStoreIdAndAppKey(TENANT_ID, STORE_ID, APP_KEY)).thenReturn(Optional.of(StoreAppSettingEntity.of(
            UUID.randomUUID(),
            TENANT_ID,
            STORE_ID,
            APP_KEY,
            true,
            true,
            "{}",
            null,
            null,
            null
        )));

        AppGateDecision decision = service.evaluate(new AppGateAccessRequest(
            APP_KEY,
            TENANT_ID,
            STORE_ID,
            "reservation.today_view",
            actor()
        ));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.denyReason()).isEqualTo(AppGateDenyReason.TENANT_APP_EXPIRED);
    }

    private static CurrentActor actor() {
        return CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("reservation.today_view"),
            Set.of(STORE_ID)
        );
    }
}
