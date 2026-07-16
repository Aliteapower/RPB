package com.rpb.reservation.customer.api;

import com.rpb.reservation.customer.application.CustomerPhoneLookupError;
import com.rpb.reservation.customer.application.CustomerPhoneLookupResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class CustomerPhoneLookupApiErrorMapper {

    public ResponseEntity<CustomerPhoneLookupApiErrorResponse> toResponse(CustomerPhoneLookupResult result) {
        return toResponse(toApiErrorCode(result.error()));
    }

    public ResponseEntity<CustomerPhoneLookupApiErrorResponse> toResponse(CustomerPhoneLookupApiErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(CustomerPhoneLookupApiErrorResponse.of(code));
    }

    private CustomerPhoneLookupApiErrorCode toApiErrorCode(CustomerPhoneLookupError error) {
        return switch (error) {
            case INVALID_PHONE_E164 -> CustomerPhoneLookupApiErrorCode.INVALID_PHONE_E164;
            case FORBIDDEN -> CustomerPhoneLookupApiErrorCode.FORBIDDEN;
            case STORE_SCOPE_MISMATCH -> CustomerPhoneLookupApiErrorCode.STORE_SCOPE_MISMATCH;
            case PERSISTENCE_ERROR -> CustomerPhoneLookupApiErrorCode.PERSISTENCE_ERROR;
        };
    }
}
