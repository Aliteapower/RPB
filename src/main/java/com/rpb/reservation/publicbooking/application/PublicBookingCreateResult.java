package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.reservation.application.ReservationCreateResult;

public record PublicBookingCreateResult(
    boolean success,
    PublicBookingError error,
    ReservationCreateResult reservation
) {

    public static PublicBookingCreateResult success(ReservationCreateResult reservation) {
        return new PublicBookingCreateResult(true, null, reservation);
    }

    public static PublicBookingCreateResult failure(PublicBookingError error) {
        return new PublicBookingCreateResult(false, error, null);
    }
}
