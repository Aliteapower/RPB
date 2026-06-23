package com.rpb.reservation.customer.application.service;

import com.rpb.reservation.customer.application.CustomerPhoneLookupError;
import com.rpb.reservation.customer.application.CustomerPhoneLookupQuery;
import com.rpb.reservation.customer.application.CustomerPhoneLookupResult;
import com.rpb.reservation.customer.application.port.out.CustomerLookupReadPort;
import org.springframework.stereotype.Service;

@Service
public class CustomerPhoneLookupApplicationService {

    private final CustomerLookupReadPort customerLookupReadPort;

    public CustomerPhoneLookupApplicationService(CustomerLookupReadPort customerLookupReadPort) {
        this.customerLookupReadPort = customerLookupReadPort;
    }

    public CustomerPhoneLookupResult lookup(CustomerPhoneLookupQuery query) {
        try {
            return customerLookupReadPort.findActiveByPhone(query.scope(), query.phone())
                .map(CustomerPhoneLookupResult::found)
                .orElseGet(CustomerPhoneLookupResult::notFound);
        } catch (RuntimeException exception) {
            return CustomerPhoneLookupResult.failure(CustomerPhoneLookupError.PERSISTENCE_ERROR);
        }
    }
}
