package com.rpb.reservation.reservation.api;

public record ReservationShareIntentResponse(
    boolean success,
    String channel
) {
    public static ReservationShareIntentResponse success(String channel) {
        return new ReservationShareIntentResponse(true, channel);
    }
}
