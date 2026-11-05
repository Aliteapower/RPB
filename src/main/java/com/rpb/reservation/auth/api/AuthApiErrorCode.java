package com.rpb.reservation.auth.api;

import org.springframework.http.HttpStatus;

public enum AuthApiErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "auth.forbidden"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "auth.rate_limited"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "auth.unauthenticated"),
    CAPTCHA_REQUIRED(HttpStatus.BAD_REQUEST, "auth.captcha_required"),
    CAPTCHA_EXPIRED(HttpStatus.BAD_REQUEST, "auth.captcha_expired"),
    CAPTCHA_MISMATCH(HttpStatus.BAD_REQUEST, "auth.captcha_mismatch"),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "auth.password_policy_violation"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "auth.invalid_credentials"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "auth.account_disabled"),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "auth.request_invalid");

    private final HttpStatus httpStatus;
    private final String messageKey;

    AuthApiErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String messageKey() {
        return messageKey;
    }
}
