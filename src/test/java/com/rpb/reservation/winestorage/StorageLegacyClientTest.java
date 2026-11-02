package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.api.*;
import com.rpb.reservation.winestorage.application.*;
import com.rpb.reservation.winestorage.persistence.*;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageLegacyClientTest {
 @Test void obsoleteStaffFlowIsRejectedAfterScopeChecksAndNeverWrites()throws Exception{
  UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);
  var actor=new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of(),Set.of("wine_storage.deliver"),Set.of(store));
  var identity=mock(StorageIdentity.class);when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);
  var access=mock(StorageAccess.class);var repo=mock(StorageRepository.class);var commands=mock(StorageCommands.class);
  var withdrawals=new StorageWithdrawalService(access,repo,commands,Clock.systemUTC());
  var mvc=standaloneSetup(new StorageStaffController(identity,null,null,withdrawals,null,null,null,null,null,null)).setControllerAdvice(new StorageErrorAdvice()).addFilters(new StorageRequestSecurityFilter()).build();
  String path="/api/v1/stores/"+store+"/wine-storage/intents",body="{\"customerId\":\""+customer+"\",\"selections\":[],\"idempotencyKey\":\"legacy\"}";
  for(int n=0;n<2;n++)mvc.perform(post(path).header("X-RPB-Storage","1").contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORAGE_CLIENT_UPDATE_REQUIRED")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未保存")));
  verify(access,times(2)).staff(scope,actor,"wine_storage.deliver",false);verify(access,times(2)).customerId(scope,customer);verifyNoInteractions(repo,commands);
  doThrow(StorageException.forbidden()).when(access).staff(scope,actor,"wine_storage.deliver",false);
  mvc.perform(post(path).header("X-RPB-Storage","1").contentType("application/json").content(body)).andExpect(status().isForbidden());
  reset(access);doThrow(StorageException.missing()).when(access).customerId(scope,customer);
  mvc.perform(post(path).header("X-RPB-Storage","1").contentType("application/json").content(body)).andExpect(status().isNotFound());
  when(identity.staff()).thenThrow(new StorageException("STORAGE_UNAUTHENTICATED",401,"login"));
  mvc.perform(post(path).header("X-RPB-Storage","1").contentType("application/json").content(body)).andExpect(status().isUnauthorized());verifyNoInteractions(repo,commands);
 }
}
