package com.rpb.reservation.winestorage;

import com.rpb.reservation.appgate.application.*;
import com.rpb.reservation.customer.application.CustomerDirectoryQuery;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.winestorage.api.*;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.persistence.StorageInventoryRepository;
import com.rpb.reservation.winestorage.domain.StorageException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageInventoryQueryTest {
 UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();StoreScope scope=new StoreScope(new TenantId(tenant),store);
 CurrentActor actor=new CurrentActor(tenant,UUID.randomUUID(),"store_staff",Set.of("store_staff"),Set.of("wine_storage.view"),Set.of(store));
 StorageInventoryRepository repository=mock(StorageInventoryRepository.class);AppGateService gate=mock(AppGateService.class);SharedStoreIdentityQuery stores=mock(SharedStoreIdentityQuery.class);
 Clock clock=Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"),ZoneOffset.UTC);
 StorageInventoryQuery query(){var s=mock(PlatformStore.class);when(s.timezone()).thenReturn("Asia/Singapore");when(stores.active(tenant,store)).thenReturn(Optional.of(s));return new StorageInventoryQuery(new StorageAccess(gate,stores,mock(CustomerDirectoryQuery.class)),repository,clock);}
 @Test void historyInventoryRequiresStoreAndViewPermissionButDoesNotRequireNewBusinessEnabled(){
  var q=query();q.list(scope,actor,"  小兰  ","all",0,50);verify(repository).list(scope,"小兰","all",0,50,clock.instant());verifyNoInteractions(gate);
  clearInvocations(repository);
  for(CurrentActor denied:List.of(new CurrentActor(UUID.randomUUID(),actor.actorId(),"staff",actor.roles(),actor.permissions(),Set.of(store)),new CurrentActor(tenant,actor.actorId(),"staff",actor.roles(),actor.permissions(),Set.of()),new CurrentActor(tenant,actor.actorId(),"staff",actor.roles(),Set.of(),Set.of(store)),new CurrentActor(tenant,actor.actorId(),"platform_admin",actor.roles(),actor.permissions(),Set.of(store))))assertThatThrownBy(()->q.list(scope,denied,"","all",0,50)).isInstanceOf(StorageException.class);
  verifyNoInteractions(repository);
 }
 @Test void invalidFiltersNeverQueryTheDatabase(){var q=query();for(String status:List.of("unknown","ALL"))assertThatThrownBy(()->q.list(scope,actor,"",status,0,50)).isInstanceOf(StorageException.class);for(int page:List.of(-1,100001))assertThatThrownBy(()->q.list(scope,actor,"","all",page,50)).isInstanceOf(StorageException.class);for(int size:List.of(0,101))assertThatThrownBy(()->q.list(scope,actor,"","all",0,size)).isInstanceOf(StorageException.class);assertThatThrownBy(()->q.list(scope,actor,"a".repeat(121),"all",0,50)).isInstanceOf(StorageException.class);verifyNoInteractions(repository);}
 @Test void apiBindsAuthenticatedScopeAndMapsInvalidForbiddenAndAnonymous()throws Exception{
  var q=query();var identity=mock(StorageIdentity.class);when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);
  when(repository.list(scope,"","all",0,50,clock.instant())).thenReturn(new StorageInventoryDtos.Page(List.of(),new StorageInventoryDtos.Summary(0,0,0,0,0),0,50,false));
  var mvc=standaloneSetup(new StorageInventoryController(identity,q)).setControllerAdvice(new StorageErrorAdvice()).build();String path="/api/v1/stores/"+store+"/wine-storage/inventory";
  mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.summary.bottleCount").value(0)).andExpect(jsonPath("$.hasMore").value(false));
  mvc.perform(get(path).param("status","bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STORAGE_INVALID"));
  mvc.perform(get(path).param("page","NaN")).andExpect(status().isBadRequest());
  when(identity.staff()).thenReturn(new CurrentActor(tenant,actor.actorId(),"staff",Set.of(),Set.of(),Set.of(store)));mvc.perform(get(path)).andExpect(status().isForbidden());
  when(identity.staff()).thenThrow(new StorageException("STORAGE_UNAUTHENTICATED",401,"login"));mvc.perform(get(path)).andExpect(status().isUnauthorized());
 }
}
