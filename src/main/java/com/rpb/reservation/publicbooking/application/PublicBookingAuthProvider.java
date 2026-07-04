package com.rpb.reservation.publicbooking.application;

public record PublicBookingAuthProvider(
    String provider,
    String clientId
) {
}
