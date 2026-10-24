package com.rpb.reservation.customerauth.application;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customer.application.port.out.CustomerPrivacyConsentPort;
import com.rpb.reservation.customerauth.api.CustomerSelfRegistrationController;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.publicbooking.application.PublicBookingStoreProfile;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class CustomerSelfRegistrationApiTest {
 private final UUID tenant=UUID.randomUUID(),store=UUID.randomUUID(),customer=UUID.randomUUID();
 private final CustomerManagementApplicationService customers=mock(CustomerManagementApplicationService.class);
 private final CustomerPrivacyConsentPort consents=mock(CustomerPrivacyConsentPort.class);
 private final CustomerRegistrationThrottleService throttle=mock(CustomerRegistrationThrottleService.class);
 private final PublicBookingStoreRepositoryPort stores=mock(PublicBookingStoreRepositoryPort.class);
 private MockMvc mvc;
 private String body(){return "{\"storeId\":\""+store+"\",\"phoneE164\":\"+6581112472\",\"privacyConsent\":{\"accepted\":true,\"noticeVersion\":\"customer-profile-v1\"}}";}
 @BeforeEach void prepare(){
  when(stores.findActiveStoreProfileByStoreId(store)).thenReturn(Optional.of(new PublicBookingStoreProfile(new StoreScope(new TenantId(tenant),store),"店面","Asia/Singapore",null,null,null,null,null)));
  when(customers.create(any(),any())).thenReturn(new CustomerManagementItem(customer,"C",null,null,"+6581112472",null,"active",null,null));
  mvc=MockMvcBuilders.standaloneSetup(new CustomerSelfRegistrationController(new CustomerSelfRegistrationService(customers,consents,throttle),stores)).build();
 }
 @Test void noticeRequiresAnActiveStoreButNotAMerchantSession() throws Exception{
  mvc.perform(get("/api/v1/public/customer-auth/registration-notice").param("storeId",store.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("customer-profile-v1"));
  mvc.perform(get("/api/v1/public/customer-auth/registration-notice").param("storeId",UUID.randomUUID().toString())).andExpect(status().isBadRequest());
 }
 @Test void publicRegistrationRecordsCustomerSubmission() throws Exception{
  mvc.perform(post("/api/v1/public/customer-auth/register").contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isCreated()).andExpect(jsonPath("$.customerId").value(customer.toString()));verify(consents).appendSelfRegistration(any(),eq(customer),eq(CustomerPrivacyNotice.CURRENT));
 }
 @Test void crossSiteUncheckedAndMissingConfirmationCannotCreate() throws Exception{
  mvc.perform(post("/api/v1/public/customer-auth/register").header("Sec-Fetch-Site","cross-site").contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/public/customer-auth/register").contentType(MediaType.APPLICATION_JSON).content(body().replace("true","false"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/public/customer-auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"storeId\":\""+store+"\"}")).andExpect(status().isBadRequest());verifyNoInteractions(customers,consents,throttle);
 }
 @Test void existingPhoneCannotBeClaimed() throws Exception{
  when(customers.create(any(),any())).thenThrow(new CustomerManagementException(CustomerManagementError.CUSTOMER_PHONE_CONFLICT));
  mvc.perform(post("/api/v1/public/customer-auth/register").contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("phone_already_registered"));verifyNoInteractions(consents);
 }
 @Test void throttledRegistrationCannotCreateAProfile() throws Exception{
  doThrow(new CustomerAuthServiceException(CustomerAuthError.RATE_LIMITED)).when(throttle).check(any(),any(),any());mvc.perform(post("/api/v1/public/customer-auth/register").contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isTooManyRequests());verifyNoInteractions(customers,consents);
 }
}
