package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.reservation.application.ReservationCancelError;
import com.rpb.reservation.reservation.status.ReservationStatus;

public class ReservationCancellationRule {

    public ReservationCancelError validateFreshCancellation(ReservationStatus status) {
        if (status == ReservationStatus.DRAFT || status == ReservationStatus.CONFIRMED) {
            return null;
        }
        if (status == ReservationStatus.ARRIVED) {
            return ReservationCancelError.RESERVATION_CANNOT_CANCEL_ARRIVED;
        }
        if (status == ReservationStatus.SEATED) {
            return ReservationCancelError.RESERVATION_CANNOT_CANCEL_SEATED;
        }
        if (status == ReservationStatus.NO_SHOW) {
            return ReservationCancelError.RESERVATION_CANNOT_CANCEL_NO_SHOW;
        }
        if (status == ReservationStatus.COMPLETED) {
            return ReservationCancelError.RESERVATION_CANNOT_CANCEL_COMPLETED;
        }
        if (status == ReservationStatus.CANCELLED) {
            return null;
        }
        return ReservationCancelError.ILLEGAL_STATE_TRANSITION;
    }
}
