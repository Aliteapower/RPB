package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.reservation.application.ReservationCheckInError;
import com.rpb.reservation.reservation.status.ReservationStatus;

public class ReservationCheckInRule {

    public ReservationCheckInError validate(ReservationStatus status) {
        if (status == ReservationStatus.CONFIRMED || status == ReservationStatus.ARRIVED) {
            return null;
        }
        if (status == ReservationStatus.CANCELLED) {
            return ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_CANCELLED;
        }
        if (status == ReservationStatus.NO_SHOW) {
            return ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_NO_SHOW;
        }
        if (status == ReservationStatus.COMPLETED) {
            return ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_COMPLETED;
        }
        if (status == ReservationStatus.SEATED) {
            return ReservationCheckInError.RESERVATION_CANNOT_CHECK_IN_SEATED;
        }
        return ReservationCheckInError.RESERVATION_STATUS_NOT_CONFIRMED;
    }
}
