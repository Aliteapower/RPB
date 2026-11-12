package com.rpb.reservation.catalog;

import com.rpb.reservation.catalog.api.*;
import com.rpb.reservation.catalog.access.CatalogAccess;
import com.rpb.reservation.catalog.application.*;
import com.rpb.reservation.catalog.application.CatalogDtos.ProductPage;
import com.rpb.reservation.catalog.persistence.*;
import com.rpb.reservation.platform.application.*;
import com.rpb.reservation.walkin.api.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageCatalogApiTest {
 @Test void storageListingBindsAuthenticatedScopeAndRejectsInvalidOrUnauthorizedRequests()throws Exception{
  UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);var actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("catalog.view"),Set.of(store));
  var actors=mock(CurrentActorProvider.class);when(actors.currentActor()).thenReturn(Optional.of(actor));var stores=mock(SharedStoreIdentityQuery.class);when(stores.active(tenant,store)).thenReturn(Optional.of(mock(PlatformStore.class)));var access=new CatalogAccess(actors,stores);var repository=mock(CatalogRepository.class);var service=new CatalogService(access,repository,mock(CatalogCommands.class),mock(ProductCatalogQuery.class));
  when(repository.storageProducts(scope,"wine",1,12)).thenReturn(new ProductPage(List.of(),0,1,12,false));
  var mvc=standaloneSetup(new CatalogController(access,service,mock(CatalogBarcodeService.class))).setControllerAdvice(new CatalogErrorAdvice()).build();String path="/api/v1/stores/"+store+"/catalog/storage-products";
  mvc.perform(get(path).param("query"," wine ").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.page").value(1));verify(repository).storageProducts(scope,"wine",1,12);
  when(repository.list(scope)).thenReturn(List.of());mvc.perform(get(path.replace("storage-products","products"))).andExpect(status().isOk()).andExpect(content().json("[]"));
  clearInvocations(repository);
  for(String query:List.of("page=-1","page=100001","size=0","size=101")){String[] parts=query.split("=");mvc.perform(get(path).param(parts[0],parts[1])).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATALOG_INVALID"));}
  mvc.perform(get(path).param("query","x".repeat(121))).andExpect(status().isBadRequest());
  mvc.perform(get(path.replace(store.toString(),UUID.randomUUID().toString()))).andExpect(status().isForbidden());
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(tenant,actor.actorId(),"staff",Set.of(),Set.of(),Set.of(store))));mvc.perform(get(path)).andExpect(status().isForbidden());
  when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(path)).andExpect(status().isUnauthorized());verifyNoInteractions(repository);
 }
}
