package com.rpb.reservation.reservation.persistence.repository;

import java.time.LocalDate;

public interface ReservationCalendarSummaryProjection {
    LocalDate getBusinessDate();

    Long getReservationCount();
}
