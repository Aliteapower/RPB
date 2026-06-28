package com.rpb.reservation.reservation.application;

public record ReservationPublicShareResult(
    boolean success,
    ReservationPublicShare share,
    ReservationPublicShareError error
) {
    public static ReservationPublicShareResult success(ReservationPublicShare share) {
        return new ReservationPublicShareResult(true, share, null);
    }

    public static ReservationPublicShareResult failure(ReservationPublicShareError error) {
        return new ReservationPublicShareResult(false, null, error);
    }
}
