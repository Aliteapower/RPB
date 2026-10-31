package com.rpb.reservation.wallet;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.appgate.persistence.entity.*;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletCommercialGateTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),sibling=UUID.randomUUID();
    final OffsetDateTime now=OffsetDateTime.parse("2026-10-22T04:00:00Z");
    final PlatformAppJpaRepository apps=mock(PlatformAppJpaRepository.class);
    final TenantAppEntitlementJpaRepository tenants=mock(TenantAppEntitlementJpaRepository.class);
    final StoreAppSettingJpaRepository settings=mock(StoreAppSettingJpaRepository.class);
    final StoreAppSubscriptionRepository subscriptions=mock(StoreAppSubscriptionRepository.class);
    final AppGateService gate=new AppGateService(apps,tenants,settings,subscriptions,Clock.fixed(now.toInstant(),ZoneOffset.UTC));
    final CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("wallet.view"),Set.of(store,sibling));
    @BeforeEach void setup() {
        var app=PlatformAppEntity.of(UUID.randomUUID(),"wallet","钱包","active","/stores/:storeId/wallet",null,50,"{}");
        var entitlement=TenantAppEntitlementEntity.of(UUID.randomUUID(),tenant,"wallet","enabled",now.minusDays(1),now.plusMonths(2),"{}",null,null);
        when(apps.findByAppKey("wallet")).thenReturn(Optional.of(app));when(apps.findAllByStatusOrderBySortOrderAscAppKeyAsc("active")).thenReturn(List.of(app));
        when(tenants.findByTenantIdAndAppKey(tenant,"wallet")).thenReturn(Optional.of(entitlement));when(tenants.findAllByTenantId(tenant)).thenReturn(List.of(entitlement));
        for(var id:List.of(store,sibling)) {
            var setting=StoreAppSettingEntity.of(UUID.randomUUID(),tenant,id,"wallet",true,true,"{}",null,null,null);
            when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,id,"wallet")).thenReturn(Optional.of(setting));when(settings.findAllByTenantIdAndStoreId(tenant,id)).thenReturn(List.of(setting));
        }
    }
    @Test void customerAndStaffUseTheSameStoreExpiryAndSiblingRemainsAvailable() {
        when(subscriptions.findByTenantAndStore(tenant,store)).thenReturn(Map.of("wallet",new StoreAppSubscription("active","active",now)));
        when(subscriptions.findByTenantAndStore(tenant,sibling)).thenReturn(Map.of("wallet",new StoreAppSubscription("active","active",now.plusDays(2))));
        assertThat(gate.evaluateCommercial("wallet",tenant,store).denyReason()).isEqualTo(AppGateDenyReason.STORE_APP_EXPIRED);
        assertThat(gate.evaluate(new AppGateAccessRequest("wallet",tenant,store,"wallet.view",actor)).allowed()).isFalse();
        assertThat(gate.evaluateCommercial("wallet",tenant,sibling).allowed()).isTrue();assertThat(gate.visibleApps(actor,sibling)).hasSize(1);
    }
    @Test void walletCannotBypassSelectedStoreBillingThroughTenantOnlyGrant() {
        when(subscriptions.findByTenantAndStore(tenant,store)).thenReturn(Map.of());
        assertThat(gate.evaluateCommercial("wallet",tenant,store).allowed()).isFalse();
        assertThat(gate.evaluate(new AppGateAccessRequest("wallet",tenant,store,"wallet.view",actor)).allowed()).isFalse();
        assertThat(gate.visibleApps(actor,store)).isEmpty();
    }
}
