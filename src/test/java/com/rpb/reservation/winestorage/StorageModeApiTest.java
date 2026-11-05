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

class StorageModeApiTest {
 @Test void modeEndpointsBindAuthenticatedScopeEnforceSameOriginAndMapConflicts()throws Exception{
  UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID(),item=UUID.randomUUID();var scope=new StoreScope(new TenantId(tenant),store);
  var actor=new CurrentActor(tenant,UUID.randomUUID(),"tenant_admin",Set.of("tenant_admin"),Set.of("wine_storage.rules.manage","wine_storage.deliver"),Set.of(store));
  var identity=mock(StorageIdentity.class);var settings=mock(StorageSettingsService.class);var withdrawals=mock(StorageWithdrawalService.class);
  when(identity.staff()).thenReturn(actor);when(identity.scope(tenant,store)).thenReturn(scope);
  var body=new SettingsWrite("staff_registration",0,"key");when(settings.save(scope,actor,body)).thenReturn(new Settings("staff_registration",1));
  var mvc=standaloneSetup(new StorageSettingsController(identity,settings),new StorageStaffController(identity,null,null,withdrawals,null,null,null,null,null,null)).setControllerAdvice(new StorageErrorAdvice()).addFilters(new StorageRequestSecurityFilter()).build();
  String base="/api/v1/stores/"+store+"/wine-storage",json="{\"operationMode\":\"staff_registration\",\"version\":0,\"idempotencyKey\":\"key\"}";
  mvc.perform(post(base+"/settings").contentType("application/json").content(json)).andExpect(status().isForbidden());verifyNoInteractions(settings);
  mvc.perform(post(base+"/settings").header("X-RPB-Storage","1").header("Sec-Fetch-Site","cross-site").contentType("application/json").content(json)).andExpect(status().isForbidden());
  mvc.perform(post(base+"/settings").header("X-RPB-Storage","1").contentType("application/json").content(json)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));verify(settings).save(scope,actor,body);
  when(settings.save(scope,actor,body)).thenThrow(StorageException.conflict("版本变化"));mvc.perform(post(base+"/settings").header("X-RPB-Storage","1").contentType("application/json").content(json)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORAGE_CONFLICT"));
  var command=new IntentWrite(customer,List.of(new Selection(item,2)),"direct");when(withdrawals.directDeliver(scope,actor,command)).thenThrow(StorageException.conflict("核销模式"));
  String direct="{\"customerId\":\""+customer+"\",\"selections\":[{\"itemId\":\""+item+"\",\"quantity\":2}],\"idempotencyKey\":\"direct\"}";
  mvc.perform(post(base+"/direct-deliver").header("X-RPB-Storage","1").contentType("application/json").content(direct)).andExpect(status().isConflict());verify(withdrawals).directDeliver(scope,actor,command);
  when(identity.staff()).thenThrow(new StorageException("STORAGE_UNAUTHENTICATED",401,"login"));mvc.perform(post(base+"/direct-deliver").header("X-RPB-Storage","1").contentType("application/json").content(direct)).andExpect(status().isUnauthorized());
 }
}
