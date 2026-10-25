package com.rpb.reservation.winestorage;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.application.StorageAccess;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageAccessTest {
 UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();StoreScope scope=new StoreScope(new TenantId(tenant),store);
 AppGateService gate=mock(AppGateService.class);SharedStoreIdentityQuery stores=mock(SharedStoreIdentityQuery.class);CustomerDirectoryQuery customers=mock(CustomerDirectoryQuery.class);
 StorageAccess access=new StorageAccess(gate,stores,customers);
 CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_manager"),Set.of("wine_storage.view","wine_storage.receive","wine_storage.deliver"),Set.of(store));
 CustomerAuthPrincipal p=new CustomerAuthPrincipal(tenant,UUID.randomUUID(),customer,"a","A");
 @BeforeEach void setup(){var s=mock(PlatformStore.class);when(s.timezone()).thenReturn("Asia/Singapore");when(stores.active(tenant,store)).thenReturn(Optional.of(s));when(customers.active(scope.tenantScope(),customer)).thenReturn(Optional.of(mock(CustomerManagementItem.class)));}
 @Test void disabledUnentitledExpiredOrStoreOffDeniesNewBusinessButAllowsHistoryAndDelivery(){
  for(var reason:List.of(AppGateDenyReason.APP_DISABLED,AppGateDenyReason.TENANT_APP_NOT_ENABLED,AppGateDenyReason.TENANT_APP_EXPIRED,AppGateDenyReason.STORE_APP_NOT_ENABLED,AppGateDenyReason.STORE_APP_EXPIRED)){
   when(gate.evaluateCommercial("wine_storage",tenant,store)).thenReturn(AppGateDecision.deny("wine_storage",tenant,store,null,reason));
   when(gate.evaluate(any())).thenReturn(AppGateDecision.deny("wine_storage",tenant,store,"wine_storage.receive",reason));
   assertThatThrownBy(()->access.customer(scope,p,true)).isInstanceOf(StorageException.class);
   assertThatThrownBy(()->access.staff(scope,actor,"wine_storage.receive",true)).isInstanceOf(StorageException.class);
   access.customer(scope,p,false);access.staff(scope,actor,"wine_storage.deliver",false);
  }
 }
 @Test void tenantStoreCustomerAndSeparatePermissionsAreEnforced(){
  assertThatThrownBy(()->access.staff(scope,new CurrentActor(UUID.randomUUID(),actor.actorId(),"staff",actor.roles(),actor.permissions(),Set.of(store)),"wine_storage.view",false)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->access.staff(scope,new CurrentActor(tenant,actor.actorId(),"staff",actor.roles(),actor.permissions(),Set.of()),"wine_storage.view",false)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->access.staff(scope,actor,"wine_storage.correct",false)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->access.staff(scope,actor,"wine_storage.rules.manage",false)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->access.customer(scope,new CustomerAuthPrincipal(UUID.randomUUID(),p.authAccountId(),customer,"a","A"),false)).isInstanceOf(StorageException.class);
  assertThatThrownBy(()->access.own(customer,UUID.randomUUID())).isInstanceOf(StorageException.class);
  verifyNoInteractions(gate);
 }
}
