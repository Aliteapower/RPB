package com.rpb.reservation.reservation.status;

/**
 * Reservation statuses confirmed by governance and migration constraints.
 */
public enum ReservationStatus {
    DRAFT("draft"),
    CONFIRMED("confirmed"),
    ARRIVED("arrived"),
    SEATED("seated"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    NO_SHOW("no_show");

    private final String code;

    ReservationStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
