package com.rpb.reservation.cleaning.status;

/**
 * Cleaning statuses confirmed by schema and migration constraints.
 */
public enum CleaningStatus {
    PENDING("pending"),
    CLEANING("cleaning"),
    COMPLETED("completed"),
    RELEASED("released"),
    CANCELLED("cancelled");

    private final String code;

    CleaningStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
