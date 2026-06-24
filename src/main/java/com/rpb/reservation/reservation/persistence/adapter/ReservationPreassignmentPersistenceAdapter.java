package com.rpb.reservation.reservation.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.reservation.domain.ReservationPreassignment;
import com.rpb.reservation.reservation.persistence.entity.ReservationPreassignmentEntity;
import com.rpb.reservation.reservation.persistence.repository.ReservationResourceAssignmentProjection;
import com.rpb.reservation.reservation.persistence.repository.ReservationPreassignmentJpaRepository;
import com.rpb.reservation.reservation.value.ReservationId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationPreassignmentPersistenceAdapter implements ReservationPreassignmentRepositoryPort {
    private static final String DINING_TABLE_TYPE = "dining_table";

    private final ReservationPreassignmentJpaRepository repository;

    public ReservationPreassignmentPersistenceAdapter(ReservationPreassignmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsActiveResourceConflict(
        StoreScope scope,
        String resourceType,
        UUID resourceId,
        BusinessDate businessDate,
        TimeRange timeRange
    ) {
        return repository.existsActiveResourceConflict(
            scope.tenantId().value(),
            scope.storeId().value(),
            resourceType,
            resourceId,
            businessDate.value()
        );
    }

    @Override
    public Set<ReservationResourceAssignment> findActiveResourceAssignmentsForDate(
        StoreScope scope,
        BusinessDate businessDate
    ) {
        return repository.findActiveResourceAssignmentsForDate(
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate.value()
        ).stream()
            .map(ReservationPreassignmentPersistenceAdapter::toAssignment)
            .collect(Collectors.toSet());
    }

    @Override
    public Optional<ReservationResourceAssignment> findActiveAssignmentForReservation(
        StoreScope scope,
        UUID reservationId
    ) {
        return repository.findActiveAssignmentForReservation(
            scope.tenantId().value(),
            scope.storeId().value(),
            reservationId
        ).map(ReservationPreassignmentPersistenceAdapter::toAssignment);
    }

    @Override
    public Optional<ReservationResourceAssignment> findActiveAssignmentForResource(
        StoreScope scope,
        String resourceType,
        UUID resourceId,
        BusinessDate businessDate
    ) {
        return repository.findActiveAssignmentForResource(
            scope.tenantId().value(),
            scope.storeId().value(),
            resourceType,
            resourceId,
            businessDate.value()
        ).map(ReservationPreassignmentPersistenceAdapter::toAssignment);
    }

    @Override
    public ReservationPreassignment save(StoreScope scope, ReservationPreassignment preassignment) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReservationPreassignmentEntity saved = repository.save(ReservationPreassignmentEntity.of(
            preassignment.id(),
            scope.tenantId().value(),
            scope.storeId().value(),
            preassignment.reservationId().value(),
            preassignment.resourceType(),
            DINING_TABLE_TYPE.equals(preassignment.resourceType()) ? preassignment.resourceId() : null,
            DINING_TABLE_TYPE.equals(preassignment.resourceType()) ? null : preassignment.resourceId(),
            preassignment.status(),
            now,
            null,
            now,
            now,
            null
        ));
        return new ReservationPreassignment(
            saved.getId(),
            scope,
            new ReservationId(saved.getReservationId()),
            saved.getResourceType(),
            saved.getTableId() == null ? saved.getTableGroupId() : saved.getTableId(),
            saved.getStatus()
        );
    }

    private static ReservationResourceAssignment toAssignment(
        ReservationResourceAssignmentProjection row
    ) {
        return new ReservationResourceAssignment(
            row.getReservationId(),
            row.getReservationCode(),
            row.getReservationStatus(),
            row.getPartySize() == null ? 0 : row.getPartySize(),
            row.getReservedStartAt(),
            row.getReservedEndAt(),
            row.getCustomerName(),
            maskPhone(row.getCustomerPhoneE164()),
            row.getResourceType(),
            row.getResourceId(),
            row.getResourceCode(),
            row.getQueueTicketId(),
            row.getQueueTicketNumber(),
            row.getQueueTicketStatus()
        );
    }

    private static String maskPhone(String phoneE164) {
        if (phoneE164 == null || phoneE164.isBlank()) {
            return null;
        }
        String value = phoneE164.trim();
        int start = Math.max(0, value.length() - 4);
        return "****" + value.substring(start);
    }
}
