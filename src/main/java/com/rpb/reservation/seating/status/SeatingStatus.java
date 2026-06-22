package com.rpb.reservation.seating.status;

/**
 * Seating statuses confirmed by schema and migration constraints.
 */
public enum SeatingStatus {
    PLANNED("planned"),
    LOCKED("locked"),
    OCCUPIED("occupied"),
    COMPLETED("completed"),
    CLEANING_TRIGGERED("cleaning_triggered"),
    CANCELLED("cancelled");

    private final String code;

    SeatingStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
