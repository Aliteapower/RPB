package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.common.rule.RuleDecision;
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingError;
import com.rpb.reservation.reservation.status.ReservationStatus;
import java.util.UUID;

public class ReservationArrivedSeatingRule {

    public ReservationArrivedDirectSeatingError validateFreshSeating(ReservationStatus status) {
        if (status == ReservationStatus.ARRIVED) {
            return null;
        }
        if (status == ReservationStatus.CANCELLED) {
            return ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_CANCELLED;
        }
        if (status == ReservationStatus.NO_SHOW) {
            return ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_NO_SHOW;
        }
        if (status == ReservationStatus.COMPLETED) {
            return ReservationArrivedDirectSeatingError.RESERVATION_CANNOT_SEAT_COMPLETED;
        }
        if (status == ReservationStatus.SEATED) {
            return ReservationArrivedDirectSeatingError.RESERVATION_SEATED_WITHOUT_ACTIVE_SEATING;
        }
        return ReservationArrivedDirectSeatingError.RESERVATION_STATUS_NOT_ARRIVED;
    }

    public RuleDecision validateReservationSource(UUID reservationId) {
        return reservationId == null ? RuleDecision.deny("invalid_seating_source") : RuleDecision.allow();
    }
}
