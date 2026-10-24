package com.rpb.reservation.appgate.application;

public class StoreProductSettingsException extends RuntimeException {
    public enum Reason { INVALID_PRODUCT, STORE_NOT_FOUND, UNAVAILABLE, FORBIDDEN }
    private final Reason reason;
    public StoreProductSettingsException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
