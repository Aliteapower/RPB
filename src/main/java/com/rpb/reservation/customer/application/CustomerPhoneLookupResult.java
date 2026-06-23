package com.rpb.reservation.customer.application;

import java.util.Optional;

public record CustomerPhoneLookupResult(
    boolean success,
    Optional<CustomerPhoneLookupCustomer> customer,
    CustomerPhoneLookupError error
) {
    public CustomerPhoneLookupResult {
        customer = customer == null ? Optional.empty() : customer;
    }

    public static CustomerPhoneLookupResult found(CustomerPhoneLookupCustomer customer) {
        return new CustomerPhoneLookupResult(true, Optional.of(customer), null);
    }

    public static CustomerPhoneLookupResult notFound() {
        return new CustomerPhoneLookupResult(true, Optional.empty(), null);
    }

    public static CustomerPhoneLookupResult failure(CustomerPhoneLookupError error) {
        return new CustomerPhoneLookupResult(false, Optional.empty(), error);
    }

    public boolean found() {
        return customer.isPresent();
    }
}
