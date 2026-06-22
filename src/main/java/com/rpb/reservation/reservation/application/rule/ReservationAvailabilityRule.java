package com.rpb.reservation.reservation.application.rule;

public final class ReservationAvailabilityRule {

    public boolean canAccept(int currentUsage, int partySize, int capacityLimit) {
        return currentUsage >= 0 && partySize > 0 && capacityLimit > 0 && currentUsage + partySize <= capacityLimit;
    }
}
