package com.rpb.reservation.customer.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import java.util.Objects;

public record CustomerPhoneLookupQuery(
    TenantScope scope,
    E164Phone phone
) {
    public CustomerPhoneLookupQuery {
        Objects.requireNonNull(scope, "tenant_scope_required");
        Objects.requireNonNull(phone, "phone_required");
    }
}
