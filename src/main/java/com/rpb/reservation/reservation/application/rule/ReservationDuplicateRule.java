package com.rpb.reservation.reservation.application.rule;

public final class ReservationDuplicateRule {

    public boolean allows(boolean duplicateActiveReservation) {
        return !duplicateActiveReservation;
    }
}
