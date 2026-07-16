package com.rpb.reservation.reservation.application.rule;

import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentError;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.status.ReservationStatus;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.status.DiningTableStatus;
import org.springframework.stereotype.Component;

@Component
public class ReservationTableAssignmentRule {

    public ReservationTableAssignmentError validateReservation(Reservation reservation, boolean alreadyAssigned) {
        if (reservation.status() != ReservationStatus.CONFIRMED) {
            return ReservationTableAssignmentError.RESERVATION_NOT_ASSIGNABLE;
        }
        return alreadyAssigned ? ReservationTableAssignmentError.RESERVATION_ALREADY_ASSIGNED : null;
    }

    public ReservationTableAssignmentError validateTable(DiningTable table, PartySize partySize) {
        if (table.status() != DiningTableStatus.AVAILABLE) {
            return ReservationTableAssignmentError.TABLE_NOT_AVAILABLE;
        }
        if (!table.capacity().includes(partySize)) {
            return ReservationTableAssignmentError.TABLE_CAPACITY_INSUFFICIENT;
        }
        return null;
    }
}
