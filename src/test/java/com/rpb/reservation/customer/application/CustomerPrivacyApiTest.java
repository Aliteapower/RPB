package com.rpb.reservation.customer.application;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.rpb.reservation.customer.application.port.out.CustomerPrivacyConsentPort;
import com.rpb.reservation.tenantadmin.api.*;
import com.rpb.reservation.tenantadmin.application.*;
import com.rpb.reservation.reservation.application.service.ReservationMealPeriodManagementService;
import com.rpb.reservation.walkin.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerPrivacyApiTest {
 private final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),actor=UUID.randomUUID(),customer=UUID.randomUUID();
 private final AtomicReference<CurrentActor> principal=new AtomicReference<>();
 private final CustomerManagementApplicationService customers=mock(CustomerManagementApplicationService.class);
 private final CustomerPrivacyConsentPort records=mock(CustomerPrivacyConsentPort.class);
 private MockMvc mvc;
 private String base(){return "/api/v1/stores/"+store+"/tenant-admin";}
 @BeforeEach void prepare(){
  principal.set(new CurrentActor(tenant,actor,"tenant_admin",Set.of("tenant_admin"),Set.of("tenant.admin.manage"),Set.of(store)));
  var registration=new CustomerRegistrationService(customers,records);
  var controller=new TenantAdminController(mock(TenantAdminStaffService.class),mock(TenantAdminTableService.class),mock(TenantAdminSettingsService.class),mock(TenantAdminProfileService.class),mock(ReservationMealPeriodManagementService.class),customers,registration,new TenantAdminScopeResolver(()->Optional.ofNullable(principal.get())),mock(com.rpb.reservation.customerauth.application.CustomerActivationStatusQuery.class));
  mvc=MockMvcBuilders.standaloneSetup(controller).build();
  when(customers.create(any(),any())).thenReturn(new CustomerManagementItem(customer,"C",null,null,"+6581112471",null,"active",null,null));
 }
 @Test void anonymousAndNonAdminsCannotReadNoticeOrRecords() throws Exception{
  principal.set(null);mvc.perform(get(base()+"/customer-privacy-notice")).andExpect(status().isUnauthorized());
  principal.set(new CurrentActor(tenant,actor,"staff",Set.of("store_staff"),Set.of(),Set.of(store)));
  mvc.perform(get(base()+"/customers/"+customer+"/privacy-consents")).andExpect(status().isForbidden());verifyNoInteractions(records);
 }
 @Test void unauthorizedStoreCannotExposeTheSharedDirectory() throws Exception{
  mvc.perform(get("/api/v1/stores/"+UUID.randomUUID()+"/tenant-admin/customer-privacy-notice")).andExpect(status().isForbidden());
 }
 @Test void missingFalseAndStaleConsentDoNotCreateACustomer() throws Exception{
  for(String consent:List.of("",",\"privacyConsent\":{\"accepted\":false,\"noticeVersion\":\"customer-profile-v2\",\"entrySource\":\"wallet\"}",",\"privacyConsent\":{\"accepted\":true,\"noticeVersion\":\"old\",\"entrySource\":\"wallet\"}")){
   mvc.perform(post(base()+"/customers").contentType(MediaType.APPLICATION_JSON).content("{\"phoneE164\":\"+6581112471\""+consent+"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("REQUEST_INVALID"));
  }
  verifyNoInteractions(customers,records);
 }
 @Test void creationRegistersActualAuthenticatedActor() throws Exception{
  mvc.perform(post(base()+"/customers").contentType(MediaType.APPLICATION_JSON).content("{\"phoneE164\":\"+6581112471\",\"privacyConsent\":{\"accepted\":true,\"noticeVersion\":\"customer-profile-v2\",\"entrySource\":\"wallet\"}}")).andExpect(status().isCreated()).andExpect(jsonPath("$.customer.id").value(customer.toString()));
  verify(records).append(argThat(scope->scope.tenantId().value().equals(tenant)&&scope.storeId().value().equals(store)),eq(customer),eq(actor),eq("wallet"),eq(CustomerPrivacyNotice.CURRENT));
 }
 @Test void unknownOrCrossTenantCustomerHasNoConsentHistory() throws Exception{
  when(customers.get(any(),eq(customer))).thenThrow(new CustomerManagementException(CustomerManagementError.CUSTOMER_NOT_FOUND));
  mvc.perform(get(base()+"/customers/"+customer+"/privacy-consents")).andExpect(status().isNotFound());verifyNoInteractions(records);
 }
}
