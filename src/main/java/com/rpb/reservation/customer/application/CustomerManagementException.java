package com.rpb.reservation.customer.application;

public class CustomerManagementException extends RuntimeException {
    private final CustomerManagementError code;

    public CustomerManagementException(CustomerManagementError code) {
        super(code.name());
        this.code = code;
    }

    public CustomerManagementError code() {
        return code;
    }
}
