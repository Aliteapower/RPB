package com.rpb.reservation.table.status;

/**
 * TableLock statuses confirmed by schema and migration constraints.
 */
public enum TableLockStatus {
    ACTIVE("active"),
    RELEASED("released"),
    EXPIRED("expired"),
    CANCELLED("cancelled");

    private final String code;

    TableLockStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
