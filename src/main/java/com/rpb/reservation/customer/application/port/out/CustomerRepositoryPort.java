package com.rpb.reservation.customer.application.port.out;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.CustomerManagementItem;
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

    default List<CustomerManagementItem> listActive(TenantScope scope, String keyword, int limit, int offset) {
        throw new UnsupportedOperationException("customer_management_list_not_supported");
    }

    default int countActive(TenantScope scope, String keyword) {
        throw new UnsupportedOperationException("customer_management_count_not_supported");
    }

    default Optional<CustomerManagementItem> findManagementItem(TenantScope scope, CustomerId customerId) {
        throw new UnsupportedOperationException("customer_management_find_not_supported");
    }

    default void archive(TenantScope scope, CustomerId customerId) {
        throw new UnsupportedOperationException("customer_management_archive_not_supported");
    }
}
