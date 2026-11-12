package com.rpb.reservation.workforce;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.appgate.persistence.entity.*;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforceAppGateTest {
    @Test void workforceRequiresExplicitStoreSubscriptionAndRejectsOffExpiredAndWrongTenant(){
        UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();var now=OffsetDateTime.now();
        var apps=mock(PlatformAppJpaRepository.class);var tenants=mock(TenantAppEntitlementJpaRepository.class);
        var settings=mock(StoreAppSettingJpaRepository.class);var subscriptions=mock(StoreAppSubscriptionRepository.class);
        var gate=new AppGateService(apps,tenants,settings,subscriptions,Clock.fixed(now.toInstant(),ZoneOffset.UTC));
        var actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("workforce.view"),Set.of(store));
        var request=new AppGateAccessRequest("workforce",tenant,store,"workforce.view",actor);
        when(apps.findByAppKey("workforce")).thenReturn(Optional.of(PlatformAppEntity.of(UUID.randomUUID(),"workforce","Workforce","active","/stores/:storeId/workforce",null,70,"{}")));
        when(tenants.findByTenantIdAndAppKey(tenant,"workforce")).thenReturn(Optional.of(TenantAppEntitlementEntity.of(UUID.randomUUID(),tenant,"workforce","enabled",null,now.plusDays(30),"{}",null,null)));
        when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,"workforce")).thenReturn(Optional.of(StoreAppSettingEntity.of(UUID.randomUUID(),tenant,store,"workforce",true,true,"{}",null,null,null)));
        when(subscriptions.findByTenantAndStore(tenant,store)).thenReturn(Map.of());
        assertThat(gate.evaluate(request).denyReason()).isEqualTo(AppGateDenyReason.STORE_APP_NOT_ENABLED);
        assertThat(gate.isStoreProductProvisioned("workforce",tenant,store)).isFalse();
        when(subscriptions.findByTenantAndStore(tenant,store)).thenReturn(Map.of("workforce",new StoreAppSubscription("active","active",now.plusDays(1))));
        assertThat(gate.evaluate(request).allowed()).isTrue();
        when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,"workforce")).thenReturn(Optional.of(StoreAppSettingEntity.of(UUID.randomUUID(),tenant,store,"workforce",false,true,"{}",null,null,null)));
        assertThat(gate.evaluate(request).denyReason()).isEqualTo(AppGateDenyReason.STORE_APP_NOT_ENABLED);
        when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,"workforce")).thenReturn(Optional.of(StoreAppSettingEntity.of(UUID.randomUUID(),tenant,store,"workforce",true,true,"{}",null,null,null)));
        when(subscriptions.findByTenantAndStore(tenant,store)).thenReturn(Map.of("workforce",new StoreAppSubscription("active","active",now.minusSeconds(1))));
        assertThat(gate.evaluate(request).denyReason()).isEqualTo(AppGateDenyReason.STORE_APP_EXPIRED);
    }
}
