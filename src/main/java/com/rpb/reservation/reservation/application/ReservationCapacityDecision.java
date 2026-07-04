package com.rpb.reservation.reservation.application;

public record ReservationCapacityDecision(
    boolean accepted,
    int capacityLimit,
    int currentUsage,
    String reasonCode
) {

    public static ReservationCapacityDecision accept(int capacityLimit, int currentUsage) {
        return new ReservationCapacityDecision(true, capacityLimit, currentUsage, null);
    }

    public static ReservationCapacityDecision reject(int capacityLimit, int currentUsage, String reasonCode) {
        return new ReservationCapacityDecision(false, capacityLimit, currentUsage, reasonCode);
    }
}
