package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.api.*;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.rpb.reservation.winestorage.application.StorageDtos.*;

class StorageLocationApiTest {
 @Test void endpointsBindScopeAndVersionsAndKeepAuthenticationAndSameOriginProtection()throws Exception{
  UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),id=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);
  var actor=new CurrentActor(tenant,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),Set.of("wine_storage.view","wine_storage.rules.manage"),Set.of(store));
  var identity=mock(StorageIdentity.class);var service=mock(StorageLocationService.class);
  when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);
  when(service.list(scope,actor)).thenReturn(List.of(new StorageLocation(id,"前台寄存处",true,0)));
  var body=new LocationWrite("冷藏柜",0,"key");when(service.save(scope,actor,id,body)).thenReturn(new StorageLocation(id,"冷藏柜",true,1));
  var mvc=standaloneSetup(new StorageLocationController(identity,service)).setControllerAdvice(new StorageErrorAdvice()).addFilters(new StorageRequestSecurityFilter()).build();String base="/api/v1/stores/"+store+"/wine-storage/locations";
  mvc.perform(post(base).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(service);
  mvc.perform(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$[0].defaultLocation").value(true));
  mvc.perform(post(base+"/"+id).header("X-RPB-Storage","1").contentType("application/json").content("{\"name\":\"冷藏柜\",\"version\":0,\"idempotencyKey\":\"key\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  when(service.save(scope,actor,id,body)).thenThrow(StorageException.conflict("版本变化"));
  mvc.perform(post(base+"/"+id).header("X-RPB-Storage","1").contentType("application/json").content("{\"name\":\"冷藏柜\",\"version\":0,\"idempotencyKey\":\"key\"}")).andExpect(status().isConflict());
  when(identity.staff()).thenThrow(new StorageException("STORAGE_UNAUTHENTICATED",401,"login"));mvc.perform(get(base)).andExpect(status().isUnauthorized());
 }
}
