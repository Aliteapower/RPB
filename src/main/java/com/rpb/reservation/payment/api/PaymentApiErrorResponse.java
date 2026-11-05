package com.rpb.reservation.payment.api;

public record PaymentApiErrorResponse(
    boolean success,
    String code,
    String message
) {
    public static PaymentApiErrorResponse of(PaymentApiErrorCode code) {
        return new PaymentApiErrorResponse(false, code.name(), code.name());
    }
}
