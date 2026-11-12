package com.rpb.reservation.customer.application.port.out;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.application.CustomerPrivacyNotice;
import com.rpb.reservation.customer.application.CustomerPrivacyConsentRecord;
import java.util.List;
import java.util.UUID;
public interface CustomerPrivacyConsentPort {
 void append(StoreScope scope,UUID customerId,UUID actorId,String entrySource,CustomerPrivacyNotice notice);
 void appendSelfRegistration(StoreScope scope,UUID customerId,CustomerPrivacyNotice notice);
 List<CustomerPrivacyConsentRecord> list(TenantScope scope,UUID customerId);
}
