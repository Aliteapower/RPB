package com.rpb.reservation.customerauth.application;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customer.application.port.out.CustomerPrivacyConsentPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CustomerSelfRegistrationService {
 private final CustomerManagementApplicationService customers;
 private final CustomerPrivacyConsentPort consents;
 private final CustomerRegistrationThrottleService throttle;
 public CustomerSelfRegistrationService(CustomerManagementApplicationService customers,CustomerPrivacyConsentPort consents,CustomerRegistrationThrottleService throttle){this.customers=customers;this.consents=consents;this.throttle=throttle;}
 @Transactional
 public CustomerManagementItem register(StoreScope scope,CustomerManagementCommand profile,Boolean accepted,String noticeVersion,String remoteAddr){
  if(!Boolean.TRUE.equals(accepted)||!CustomerPrivacyNotice.CURRENT.version().equals(noticeVersion)||profile==null)throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
  throttle.check(scope.tenantScope(),profile.phoneE164(),remoteAddr);
  var customer=customers.create(scope.tenantScope(),profile);
  consents.appendSelfRegistration(scope,customer.id(),CustomerPrivacyNotice.CURRENT);
  return customer;
 }
}
