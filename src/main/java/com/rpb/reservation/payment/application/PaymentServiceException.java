package com.rpb.reservation.payment.application;

public class PaymentServiceException extends RuntimeException {
    private final PaymentServiceErrorCode code;

    public PaymentServiceException(PaymentServiceErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PaymentServiceErrorCode code() {
        return code;
    }
}
