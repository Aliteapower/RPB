package com.rpb.reservation.catalog;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.domain.CatalogException;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CatalogAccessTest {
 @Test void managementRequiresTenantStoreAndPermissionAndRejectsPlatformIdentity(){UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);var actors=mock(CurrentActorProvider.class);var stores=mock(SharedStoreIdentityQuery.class);var access=new CatalogAccess(actors,stores);when(actors.currentActor()).thenReturn(Optional.empty());assertThatThrownBy(access::actor).isInstanceOf(CatalogException.class);var actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("store_staff"),Set.of("catalog.view"),Set.of(store));when(stores.active(tenant,store)).thenReturn(Optional.of(mock(PlatformStore.class)));access.require(scope,actor,"catalog.view");assertThatThrownBy(()->access.require(scope,actor,"catalog.manage")).isInstanceOf(CatalogException.class);assertThatThrownBy(()->access.require(new StoreScope(new TenantId(UUID.randomUUID()),store),actor,"catalog.view")).isInstanceOf(CatalogException.class);when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(null,UUID.randomUUID(),"platform_admin",Set.of("platform_admin"),Set.of("catalog.manage"),Set.of())));assertThatThrownBy(access::actor).isInstanceOf(CatalogException.class);}
}
