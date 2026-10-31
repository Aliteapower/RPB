package com.rpb.reservation.customer.application;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customer.application.port.out.CustomerPrivacyConsentPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRegistrationService {
 private final CustomerManagementApplicationService customers;
 private final CustomerPrivacyConsentPort consents;
 public CustomerRegistrationService(CustomerManagementApplicationService customers,CustomerPrivacyConsentPort consents){this.customers=customers;this.consents=consents;}
 @Transactional
 public CustomerManagementItem create(StoreScope scope,UUID actor,CustomerManagementCommand profile,CustomerPrivacyConsentCommand consent){
  if(actor==null||consent==null||!Boolean.TRUE.equals(consent.accepted())||!CustomerPrivacyNotice.CURRENT.version().equals(consent.noticeVersion())||!("wallet".equals(consent.entrySource())||"tenant_admin".equals(consent.entrySource())))throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
  var customer=customers.create(scope.tenantScope(),profile);
  consents.append(scope,customer.id(),actor,consent.entrySource(),CustomerPrivacyNotice.CURRENT);
  return customer;
 }
 @Transactional(readOnly=true)
 public List<CustomerPrivacyConsentRecord> records(StoreScope scope,UUID customer){customers.get(scope.tenantScope(),customer);return consents.list(scope.tenantScope(),customer);}
 public CustomerPrivacyNotice notice(){return CustomerPrivacyNotice.CURRENT;}
}
