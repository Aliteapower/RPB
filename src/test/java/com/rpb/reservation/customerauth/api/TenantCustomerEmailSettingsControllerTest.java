package com.rpb.reservation.customerauth.api;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.publicbooking.application.port.out.*;
import com.rpb.reservation.publicbooking.application.PublicBookingStoreProfile;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.tenantadmin.api.*;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantCustomerEmailSettingsControllerTest {
 final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID();
 final StoreScope scope=new StoreScope(new TenantId(tenant),store);
 final CurrentActorProvider actors=mock(CurrentActorProvider.class);
 final AppGateService gate=mock(AppGateService.class);
 final PublicBookingStoreRepositoryPort stores=mock(PublicBookingStoreRepositoryPort.class);
 final TenantCustomerEmailAdminService service=mock(TenantCustomerEmailAdminService.class);
 final TenantCustomerEmailSettingsController controller=new TenantCustomerEmailSettingsController(service,new TenantAdminScopeResolver(actors),new CustomerEmailManagementAccess(gate,stores));
 @BeforeEach void scoped(){
  when(actors.currentActor()).thenReturn(Optional.of(new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store))));
  when(stores.findActiveStoreProfile(scope)).thenReturn(Optional.of(new PublicBookingStoreProfile(scope,"Store","UTC",null,null,null,null,null)));
  when(gate.evaluateCommercial(anyString(),eq(tenant),eq(store))).thenAnswer(i->AppGateDecision.deny(i.getArgument(0),tenant,store,null,AppGateDenyReason.TENANT_APP_NOT_ENABLED));
  when(service.getEmailSettings(scope)).thenReturn(CustomerEmailSettings.disabled());
  when(service.emailSettingsScope(scope)).thenReturn("unconfigured");
 }
 @ParameterizedTest @ValueSource(strings={"wine_storage","wallet","reservation_queue"})
 void anyCustomerProductCanConfigureWithoutBooking(String product)throws Exception{
  when(gate.evaluateCommercial(product,tenant,store)).thenReturn(AppGateDecision.allow(product,tenant,store,null));
  when(service.saveEmailSettings(eq(scope),any(),any())).thenReturn(new CustomerEmailSettings(true,"smtp","from@example.test","Sender","smtp.example.test",587,"user","secret",true,true));
  var mvc=MockMvcBuilders.standaloneSetup(controller).build();
  for(String prefix:List.of("/customer-auth","/public-booking/customer-auth")){
   String path="/api/v1/stores/"+store+"/tenant-admin"+prefix+"/email-settings";
   mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.configurationScope").value("unconfigured")).andExpect(jsonPath("$.smtpPassword").doesNotExist());
   mvc.perform(put(path).contentType("application/json").content("{\"enabled\":true,\"smtpPassword\":\"secret\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.configurationScope").value("tenant")).andExpect(jsonPath("$.smtpPassword").doesNotExist());
  }
 }
 @Test void allClosedDeniedAndRestoredWithoutDeletingSettings()throws Exception{
  var mvc=MockMvcBuilders.standaloneSetup(controller).build();String path="/api/v1/stores/"+store+"/tenant-admin/customer-auth/email-settings";
  mvc.perform(get(path)).andExpect(status().isForbidden());mvc.perform(put(path).contentType("application/json").content("{}" )).andExpect(status().isForbidden());verifyNoInteractions(service);
  when(gate.evaluateCommercial("wine_storage",tenant,store)).thenReturn(AppGateDecision.allow("wine_storage",tenant,store,null));
  mvc.perform(get(path)).andExpect(status().isOk());verify(service).getEmailSettings(scope);
 }
 @Test void unauthorizedIdentitiesNeverReadConfiguration()throws Exception{
  var mvc=MockMvcBuilders.standaloneSetup(controller).build();String path="/api/v1/stores/"+store+"/tenant-admin/customer-auth/email-settings";
  when(actors.currentActor()).thenReturn(Optional.empty());mvc.perform(get(path)).andExpect(status().isUnauthorized());
  for(CurrentActor actor:List.of(new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("staff"),Set.of("tenant.admin.manage"),Set.of(store)),new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("tenant_admin"),Set.of(),Set.of(store)),new CurrentActor(tenant,UUID.randomUUID(),"staff",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(UUID.randomUUID())))){
   when(actors.currentActor()).thenReturn(Optional.of(actor));mvc.perform(get(path)).andExpect(status().isForbidden());mvc.perform(put(path).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  }verifyNoInteractions(gate,service);
 }
 @Test void foreignPhysicalStoreDeniedBeforeProductEvaluation()throws Exception{
  when(stores.findActiveStoreProfile(scope)).thenReturn(Optional.empty());
  var mvc=MockMvcBuilders.standaloneSetup(controller).build();mvc.perform(get("/api/v1/stores/"+store+"/tenant-admin/customer-auth/email-settings")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("STORE_SCOPE_MISMATCH"));verifyNoInteractions(gate,service);
 }
}
