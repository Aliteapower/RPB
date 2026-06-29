package com.rpb.reservation.reservation.application;

public record ReservationShareIntentResult(
    boolean success,
    ReservationShareInfoError error,
    String channel
) {
    public static ReservationShareIntentResult success(String channel) {
        return new ReservationShareIntentResult(true, null, channel);
    }

    public static ReservationShareIntentResult failure(ReservationShareInfoError error) {
        return new ReservationShareIntentResult(false, error, null);
    }
}
