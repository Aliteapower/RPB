package com.rpb.reservation.customer.application.port.out;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.value.CustomerId;

/** Modules retaining customer-owned assets may reject destructive lifecycle changes. */
public interface CustomerLifecycleGuard {
    void beforeArchive(TenantScope scope,CustomerId customer);
}
