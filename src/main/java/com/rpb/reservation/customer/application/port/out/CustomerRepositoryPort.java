package com.rpb.reservation.customer.application.port.out;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import java.util.List;
import java.util.Optional;

public interface CustomerRepositoryPort {

    Optional<Customer> findById(TenantScope scope, CustomerId customerId);

    Optional<Customer> findByCode(TenantScope scope, String customerCode);

    Optional<Customer> findByPhone(TenantScope scope, E164Phone phone);

    List<Customer> searchNoPhoneCandidates(TenantScope scope, String lookupText);

    Customer save(TenantScope scope, Customer customer);
}
