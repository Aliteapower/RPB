package com.rpb.reservation.payment.api;

public class PaymentApiException extends RuntimeException {
    private final PaymentApiErrorCode code;

    public PaymentApiException(PaymentApiErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public PaymentApiErrorCode code() {
        return code;
    }
}
