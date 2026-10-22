package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.application.CustomerManagementItem;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.customerauth.application.port.out.CustomerAuthRepositoryPort;
import java.util.*;
import org.springframework.stereotype.Service;

/** Reuses tenant Customer and verified-login account identity; no parallel wallet customer directory. */
@Service
public class CustomerIdentityQuery {
    private final CustomerRepositoryPort customers;
    private final CustomerAuthRepositoryPort identities;
    public CustomerIdentityQuery(CustomerRepositoryPort customers,CustomerAuthRepositoryPort identities) {
        this.customers=customers;this.identities=identities;
    }
    public List<CustomerManagementItem> search(TenantScope scope,String query) {
        var candidates=customers.listActive(scope,query==null?null:query.trim(),50,0);
        var ids=identities.activeCustomerIds(scope.tenantId().value(),candidates.stream().map(CustomerManagementItem::id).toList());
        return candidates.stream().filter(c -> ids.contains(c.id())).limit(20).toList();
    }
    public Optional<CustomerManagementItem> authenticated(TenantScope scope,UUID id) {
        if(!identities.activeCustomerIds(scope.tenantId().value(),List.of(id)).contains(id)) return Optional.empty();
        return customers.findManagementItem(scope,new CustomerId(id)).filter(c -> "active".equals(c.status()));
    }
}
