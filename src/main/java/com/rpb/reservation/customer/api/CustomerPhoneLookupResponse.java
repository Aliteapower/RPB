package com.rpb.reservation.customer.api;

import com.rpb.reservation.customer.application.CustomerPhoneLookupCustomer;
import com.rpb.reservation.customer.application.CustomerPhoneLookupResult;

public record CustomerPhoneLookupResponse(
    boolean success,
    boolean found,
    CustomerPhoneLookupCustomerResponse customer
) {
    public static CustomerPhoneLookupResponse from(CustomerPhoneLookupResult result) {
        return result.customer()
            .map(CustomerPhoneLookupResponse::found)
            .orElseGet(CustomerPhoneLookupResponse::notFound);
    }

    private static CustomerPhoneLookupResponse found(CustomerPhoneLookupCustomer customer) {
        return new CustomerPhoneLookupResponse(
            true,
            true,
            new CustomerPhoneLookupCustomerResponse(
                customer.customerId(),
                customer.displayName(),
                customer.nickname(),
                customer.phoneE164()
            )
        );
    }

    private static CustomerPhoneLookupResponse notFound() {
        return new CustomerPhoneLookupResponse(true, false, null);
    }
}
