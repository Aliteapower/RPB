package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.port.out.CustomerActivationPort;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerActivationStatusQuery {
    private final CustomerActivationPort codes;
    public CustomerActivationStatusQuery(CustomerActivationPort codes) { this.codes = codes; }
    public Map<UUID, CustomerActivationState> states(StoreScope scope, Collection<UUID> customers) {
        if (customers.isEmpty()) return Map.of();
        return codes.states(scope.tenantScope(), Set.copyOf(customers));
    }
}
