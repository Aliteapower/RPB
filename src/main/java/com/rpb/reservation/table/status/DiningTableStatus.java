package com.rpb.reservation.table.status;

/**
 * DiningTable statuses confirmed by governance and migration constraints.
 */
public enum DiningTableStatus {
    AVAILABLE("available"),
    LOCKED("locked"),
    RESERVED("reserved"),
    OCCUPIED("occupied"),
    CLEANING("cleaning"),
    INACTIVE("inactive");

    private final String code;

    DiningTableStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
