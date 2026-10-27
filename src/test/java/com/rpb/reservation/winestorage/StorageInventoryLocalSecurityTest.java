package com.rpb.reservation.winestorage;

import com.rpb.reservation.winestorage.api.*;
import com.rpb.reservation.winestorage.application.StorageInventoryQuery;
import com.rpb.reservation.winestorage.domain.StorageException;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StorageInventoryController.class)
@Import({LocalRuntimeSecurityConfiguration.class,com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider.class,com.rpb.reservation.appgate.api.AppGateApiErrorMapper.class})
@ActiveProfiles("local")
@TestPropertySource(properties="rpb.local-auth.enabled=true")
class StorageInventoryLocalSecurityTest {
 @Autowired MockMvc mvc;
 @MockBean StorageIdentity identity;
 @MockBean StorageInventoryQuery query;
 @MockBean com.rpb.reservation.appgate.application.AppGateService appGate;
 @MockBean com.rpb.reservation.appgate.application.AppGateDenialAuditService audit;
 @Test void onlyExactStaffGetRouteReachesIdentityOtherMethodsAndGuessedRoutesRemainDenied()throws Exception{
  when(identity.staff()).thenThrow(new StorageException("STORAGE_UNAUTHENTICATED",401,"login"));String path="/api/v1/stores/20000000-0000-0000-0000-000000000983/wine-storage/inventory";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(post(path).header("X-RPB-Storage","1")).andExpect(status().isForbidden());
  mvc.perform(delete(path).header("X-RPB-Storage","1")).andExpect(status().isForbidden());
  mvc.perform(get(path+"/debug")).andExpect(status().isForbidden());
  mvc.perform(get(path.replace("/api/v1/stores/","/api/v1/public/stores/"))).andExpect(status().isForbidden());
  verifyNoInteractions(query);
 }
}
