package com.rpb.reservation.reservation.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface ReservationPreassignmentRepositoryPort {

    boolean existsActiveResourceConflict(
        StoreScope scope,
        String resourceType,
        UUID resourceId,
        BusinessDate businessDate,
        TimeRange timeRange
    );

    Set<ReservationResourceAssignment> findActiveResourceAssignmentsForDate(StoreScope scope, BusinessDate businessDate);

    default Optional<ReservationResourceAssignment> findActiveAssignmentForReservation(StoreScope scope, UUID reservationId) {
        return Optional.empty();
    }

    default Optional<ReservationResourceAssignment> findActiveAssignmentForResource(
        StoreScope scope,
        String resourceType,
        UUID resourceId,
        BusinessDate businessDate
    ) {
        return Optional.empty();
    }

    default Set<ReservationResourceTarget> findActiveResourceTargetsForDate(
        StoreScope scope,
        BusinessDate businessDate
    ) {
        return findActiveResourceAssignmentsForDate(scope, businessDate).stream()
            .map(ReservationResourceAssignment::target)
            .collect(Collectors.toSet());
    }

    ReservationPreassignment save(StoreScope scope, ReservationPreassignment preassignment);
}
