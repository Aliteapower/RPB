package com.rpb.reservation.customer.application.port.out;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.CustomerPhoneLookupCustomer;
import java.util.Optional;

public interface CustomerLookupReadPort {
    Optional<CustomerPhoneLookupCustomer> findActiveByPhone(TenantScope scope, E164Phone phone);
}
