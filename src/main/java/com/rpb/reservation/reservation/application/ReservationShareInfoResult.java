package com.rpb.reservation.reservation.application;

public record ReservationShareInfoResult(
    boolean success,
    ReservationShareInfo shareInfo,
    ReservationShareInfoError error
) {
    public static ReservationShareInfoResult success(ReservationShareInfo shareInfo) {
        return new ReservationShareInfoResult(true, shareInfo, null);
    }

    public static ReservationShareInfoResult failure(ReservationShareInfoError error) {
        return new ReservationShareInfoResult(false, null, error);
    }
}
