package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.reservation.application.ReservationArrivedToQueueError;
import com.rpb.reservation.reservation.status.ReservationStatus;

public class ReservationArrivedToQueueRule {

    public ReservationArrivedToQueueError validateFreshQueue(ReservationStatus status) {
        if (status == ReservationStatus.ARRIVED) {
            return null;
        }
        if (status == ReservationStatus.SEATED) {
            return ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_SEATED;
        }
        if (status == ReservationStatus.CANCELLED) {
            return ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_CANCELLED;
        }
        if (status == ReservationStatus.NO_SHOW) {
            return ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_NO_SHOW;
        }
        if (status == ReservationStatus.COMPLETED) {
            return ReservationArrivedToQueueError.RESERVATION_CANNOT_QUEUE_COMPLETED;
        }
        return ReservationArrivedToQueueError.RESERVATION_STATUS_NOT_ARRIVED;
    }
}
