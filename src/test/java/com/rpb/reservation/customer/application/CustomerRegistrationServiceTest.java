package com.rpb.reservation.customer.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.port.out.CustomerPrivacyConsentPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerRegistrationServiceTest {
 private final CustomerManagementApplicationService customers=mock(CustomerManagementApplicationService.class);
 private final CustomerPrivacyConsentPort records=mock(CustomerPrivacyConsentPort.class);
 private final CustomerRegistrationService service=new CustomerRegistrationService(customers,records);
 private final StoreScope scope=new StoreScope(new TenantId(UUID.randomUUID()),UUID.randomUUID());
 private final UUID actor=UUID.randomUUID(),customer=UUID.randomUUID();
 private final CustomerManagementCommand profile=new CustomerManagementCommand("姓名",null,"+6581112467",null);
 private CustomerPrivacyConsentCommand accepted(){return new CustomerPrivacyConsentCommand(true,"customer-profile-v1","wallet");}
 @Test void rejectsMissingUncheckedStaleOrForgedConsentBeforeWriting(){
  for(CustomerPrivacyConsentCommand value:new CustomerPrivacyConsentCommand[]{null,new CustomerPrivacyConsentCommand(false,"customer-profile-v1","wallet"),new CustomerPrivacyConsentCommand(true,"old","wallet"),new CustomerPrivacyConsentCommand(true,"customer-profile-v1","customer_self")}){
   assertThatThrownBy(()->service.create(scope,actor,profile,value)).isInstanceOf(CustomerManagementException.class);
  }
  verifyNoInteractions(customers,records);
 }
 @Test void registersOnlyTheActualCreatedCustomerAndServerOwnedNotice(){
  var item=new CustomerManagementItem(customer,"C",null,null,"+6581112467",null,"active",null,null);when(customers.create(scope.tenantScope(),profile)).thenReturn(item);
  assertThat(service.create(scope,actor,profile,accepted())).isEqualTo(item);
  verify(records).append(scope,customer,actor,"wallet",CustomerPrivacyNotice.CURRENT);
 }
 @Test void duplicatePhoneDoesNotRecordConsent(){
  when(customers.create(scope.tenantScope(),profile)).thenThrow(new CustomerManagementException(CustomerManagementError.CUSTOMER_PHONE_CONFLICT));
  assertThatThrownBy(()->service.create(scope,actor,profile,accepted())).isInstanceOf(CustomerManagementException.class);verifyNoInteractions(records);
 }
 @Test void recordFailureIsPropagatedForTransactionRollback(){
  when(customers.create(scope.tenantScope(),profile)).thenReturn(new CustomerManagementItem(customer,"C",null,null,"+6581112467",null,"active",null,null));doThrow(new IllegalStateException("write failed")).when(records).append(any(),any(),any(),any(),any());
  assertThatThrownBy(()->service.create(scope,actor,profile,accepted())).hasMessage("write failed");
 }
 @Test void recordsRequireAnExistingCustomerInTheSameTenant(){
  when(customers.get(scope.tenantScope(),customer)).thenThrow(new CustomerManagementException(CustomerManagementError.CUSTOMER_NOT_FOUND));
  assertThatThrownBy(()->service.records(scope,customer)).isInstanceOf(CustomerManagementException.class);verifyNoInteractions(records);
 }
}
