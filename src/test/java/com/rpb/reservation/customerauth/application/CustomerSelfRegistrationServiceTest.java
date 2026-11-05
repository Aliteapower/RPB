package com.rpb.reservation.customerauth.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customer.application.port.out.CustomerPrivacyConsentPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class CustomerSelfRegistrationServiceTest {
 private final CustomerManagementApplicationService customers=mock(CustomerManagementApplicationService.class);
 private final CustomerPrivacyConsentPort consents=mock(CustomerPrivacyConsentPort.class);
 private final CustomerRegistrationThrottleService throttle=mock(CustomerRegistrationThrottleService.class);
 private final CustomerSelfRegistrationService service=new CustomerSelfRegistrationService(customers,consents,throttle);
 private final StoreScope scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());
 private final CustomerManagementCommand profile=new CustomerManagementCommand("自行注册",null,"+6581112472",null);
 @Test void uncheckedOrStaleConsentIsRejectedBeforeRegistration(){
  assertThatThrownBy(()->service.register(scope,profile,false,"customer-profile-v2","local")).isInstanceOf(CustomerManagementException.class);
  assertThatThrownBy(()->service.register(scope,profile,true,"old","local")).isInstanceOf(CustomerManagementException.class);verifyNoInteractions(customers,consents,throttle);
 }
 @Test void selfRegistrationUsesTheCreatedCustomerAndNeverChangesExistingCredentials(){
  UUID id=UUID.randomUUID();var result=new CustomerManagementItem(id,"C",null,null,"+6581112472",null,"active",null,null);when(customers.create(scope.tenantScope(),profile)).thenReturn(result);
  assertThat(service.register(scope,profile,true,"customer-profile-v2","local")).isEqualTo(result);verify(throttle).check(scope.tenantScope(),profile.phoneE164(),"local");verify(consents).appendSelfRegistration(scope,id,CustomerPrivacyNotice.CURRENT);verify(consents,never()).append(any(),any(),any(),any(),any());
 }
 @Test void duplicatePhoneCannotBeClaimedOrGivenANewConfirmation(){
  when(customers.create(scope.tenantScope(),profile)).thenThrow(new CustomerManagementException(CustomerManagementError.CUSTOMER_PHONE_CONFLICT));
  assertThatThrownBy(()->service.register(scope,profile,true,"customer-profile-v2","local")).isInstanceOf(CustomerManagementException.class);verifyNoInteractions(consents);
 }
 @Test void rateLimitFailureCannotCreateCustomer(){
  doThrow(new CustomerAuthServiceException(CustomerAuthError.RATE_LIMITED)).when(throttle).check(any(),any(),any());assertThatThrownBy(()->service.register(scope,profile,true,"customer-profile-v2","local")).isInstanceOf(CustomerAuthServiceException.class);verifyNoInteractions(customers,consents);
 }
}
