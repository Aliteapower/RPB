package com.rpb.reservation.customerauth.application;

public class CustomerAuthServiceException extends RuntimeException {
    private final CustomerAuthError error;

    public CustomerAuthServiceException(CustomerAuthError error) {
        super(error.name());
        this.error = error;
    }

    public CustomerAuthError error() {
        return error;
    }
}
