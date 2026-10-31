package com.rpb.reservation.appgate.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.appgate.persistence.entity.StoreAppSettingEntity;
import com.rpb.reservation.appgate.persistence.repository.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.*;

class StoreProductSettingsServiceTest {
    final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();
    final StoreScope scope=new StoreScope(new TenantId(tenant),store);
    final CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"admin",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store));
    final StoreProductManagementRepository stores=mock(StoreProductManagementRepository.class);
    final StoreAppSettingJpaRepository settings=mock(StoreAppSettingJpaRepository.class);
    final AppGateCommandService commands=mock(AppGateCommandService.class);
    final AppGateService gate=mock(AppGateService.class);
    final StoreProductSettingsService service=new StoreProductSettingsService(stores,settings,commands,gate);
    @BeforeEach void prepare(){
        when(stores.isActive(scope,false)).thenReturn(true);
        when(stores.isActive(scope,true)).thenReturn(true);
        for(String key:List.of("wallet","daily_ledger","wine_storage")){when(gate.evaluateStoreActivation(key,tenant,store)).thenReturn(AppGateDecision.allow(key,tenant,store,null));when(gate.isStoreProductProvisioned(key,tenant,store)).thenReturn(true);}
    }
    StoreAppSettingEntity setting(String key,boolean enabled){return StoreAppSettingEntity.of(UUID.randomUUID(),tenant,store,key,enabled,enabled,"{}",null,null,null);}
    @Test void listKeepsPreviouslyPurchasedClosedAndExpiredProductsWithoutWriting(){
        when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,"wallet")).thenReturn(Optional.of(setting("wallet",false)));
        when(gate.evaluateStoreActivation("wallet",tenant,store)).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,AppGateDenyReason.STORE_APP_EXPIRED));
        var rows=service.list(scope);
        assertThat(rows).hasSize(3);assertThat(rows.getFirst().enabled()).isFalse();assertThat(rows.getFirst().canEnable()).isFalse();assertThat(rows.getFirst().unavailableReason()).isEqualTo("STORE_APP_EXPIRED");
        verifyNoInteractions(commands);
    }
    @Test void bothProductsCanBeDisabledAndReenabledThroughAuditedCommands(){
        for(String key:List.of("wallet","daily_ledger","wine_storage")){
            when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,key)).thenReturn(Optional.of(setting(key,true)));
            service.setEnabled(scope,actor,key,false);verify(commands).disableStoreApp(tenant,store,key,actor.actorId(),"tenant_admin");
            when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,key)).thenReturn(Optional.of(setting(key,false)));
            service.setEnabled(scope,actor,key,true);verify(commands).enableStoreApp(tenant,store,key,actor.actorId(),"tenant_admin");
        }
    }
    @Test void repeatedTargetStateDoesNotRepeatAudit(){
        when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,"wallet")).thenReturn(Optional.of(setting("wallet",false)));
        service.setEnabled(scope,actor,"wallet",false);verifyNoInteractions(commands);
    }
    @Test void expiredAndUnentitledActivationCannotWriteButCanStillBeTurnedOff(){
        for(var reason:List.of(AppGateDenyReason.APP_DISABLED,AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.TENANT_APP_EXPIRED,AppGateDenyReason.STORE_APP_EXPIRED,AppGateDenyReason.STORE_APP_NOT_ENABLED)){
            when(gate.evaluateStoreActivation("wallet",tenant,store)).thenReturn(AppGateDecision.deny("wallet",tenant,store,null,reason));
            assertThatThrownBy(()->service.setEnabled(scope,actor,"wallet",true)).isInstanceOf(StoreProductSettingsException.class);
        }
        verifyNoInteractions(commands);
    }
    @Test void unknownProductAndInactiveStoreCannotWrite(){
        assertThatThrownBy(()->service.setEnabled(scope,actor,"unknown_product",true)).isInstanceOf(StoreProductSettingsException.class);
        when(stores.isActive(scope,true)).thenReturn(false);
        assertThatThrownBy(()->service.setEnabled(scope,actor,"wallet",true)).isInstanceOf(StoreProductSettingsException.class);verifyNoInteractions(commands);
    }
    @Test void foreignTenantUnassignedStoreAndStaffCannotWrite(){
        for(var invalid:List.of(new CurrentActor(UUID.randomUUID(),actor.actorId(),"admin",actor.roles(),actor.permissions(),Set.of(store)),new CurrentActor(tenant,actor.actorId(),"admin",actor.roles(),actor.permissions(),Set.of(UUID.randomUUID())),new CurrentActor(tenant,actor.actorId(),"staff",Set.of("store_staff"),actor.permissions(),Set.of(store)))){
            assertThatThrownBy(()->service.setEnabled(scope,invalid,"wallet",false)).isInstanceOf(StoreProductSettingsException.class);
        }
        verifyNoInteractions(commands);
    }
    @Test void neverOpenedProductMustNotAppearEvenWhenPermissionOrStaleSwitchExists(){
        when(gate.evaluateStoreActivation("wine_storage",tenant,store)).thenReturn(AppGateDecision.deny("wine_storage",tenant,store,null,AppGateDenyReason.TENANT_APP_NOT_ENABLED));
        when(gate.isStoreProductProvisioned("wine_storage",tenant,store)).thenReturn(false);
        assertThat(service.list(scope)).extracting(StoreProductSetting::appKey).doesNotContain("wine_storage");
        verifyNoInteractions(commands);
    }
    @Test void everyManagedProductUsesTheSameProvisioningRule(){
        for(String key:List.of("wallet","daily_ledger","wine_storage"))when(gate.isStoreProductProvisioned(key,tenant,store)).thenReturn(false);
        assertThat(service.list(scope)).isEmpty();verifyNoInteractions(commands);
    }
    @Test void everyRegisteredProductSupportsScopedManagement(){
        for(String key:com.rpb.reservation.common.product.ProductLineEntryRegistry.managedProducts()){
            when(gate.isStoreProductProvisioned(key,tenant,store)).thenReturn(true);
            when(gate.evaluateStoreActivation(key,tenant,store)).thenReturn(AppGateDecision.allow(key,tenant,store,null));
            when(settings.findByTenantIdAndStoreIdAndAppKey(tenant,store,key)).thenReturn(Optional.of(setting(key,false)));
            service.setEnabled(scope,actor,key,true);verify(commands).enableStoreApp(tenant,store,key,actor.actorId(),"tenant_admin");
        }
        assertThat(service.list(scope)).hasSize(com.rpb.reservation.common.product.ProductLineEntryRegistry.managedProducts().size());
    }
}

