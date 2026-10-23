package com.rpb.reservation.customer.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.value.CustomerId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared tenant customer directory. Product lines own neither profiles nor login eligibility. */
@Service
@Transactional(readOnly = true)
public class CustomerDirectoryQuery {
    private final CustomerRepositoryPort customers;

    public CustomerDirectoryQuery(CustomerRepositoryPort customers) { this.customers = customers; }

    public List<CustomerManagementItem> search(TenantScope scope, String query) {
        return customers.listActive(scope, query == null ? null : query.trim(), 20, 0);
    }

    public Optional<CustomerManagementItem> active(TenantScope scope, UUID id) {
        return customers.findManagementItem(scope, new CustomerId(id)).filter(c -> "active".equals(c.status()));
    }
}
