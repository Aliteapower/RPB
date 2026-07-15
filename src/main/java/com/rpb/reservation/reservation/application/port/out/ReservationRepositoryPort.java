package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReservationRepositoryPort {

    Optional<Reservation> findById(StoreScope scope, ReservationId reservationId);

    default Optional<Reservation> findByIdForUpdate(StoreScope scope, ReservationId reservationId) {
        return findById(scope, reservationId);
    }

    Optional<Reservation> findByCode(StoreScope scope, ReservationCode reservationCode);

    boolean existsByReservationCode(StoreScope scope, ReservationCode reservationCode);

    List<Reservation> findStoreSchedule(StoreScope scope, BusinessDate businessDate, TimeRange timeRange);

    boolean existsActiveDuplicate(StoreScope scope, CustomerId customerId, TimeRange timeRange);

    default boolean existsActiveConflict(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
        return existsActiveDuplicate(scope, customerId, timeRange);
    }

    List<Reservation> findActiveConflicts(StoreScope scope, CustomerId customerId, TimeRange timeRange);

    int findActiveCapacityUsage(StoreScope scope, BusinessDate businessDate, TimeRange timeRange);

    default List<ReservationTodayViewRow> findTodayView(
        StoreScope scope,
        BusinessDate businessDate,
        Set<String> statuses
    ) {
        return List.of();
    }

    default List<ReservationCalendarSummaryRow> findCalendarSummary(
        StoreScope scope,
        LocalDate startInclusive,
        LocalDate endExclusive,
        Set<String> statuses
    ) {
        return List.of();
    }

    Reservation save(StoreScope scope, Reservation reservation);
}
