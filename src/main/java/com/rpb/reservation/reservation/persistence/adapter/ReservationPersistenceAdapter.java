package com.rpb.reservation.reservation.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationTodayViewRow;
import com.rpb.reservation.reservation.domain.Reservation;
import com.rpb.reservation.reservation.persistence.entity.ReservationEntity;
import com.rpb.reservation.reservation.persistence.mapper.ReservationMapper;
import com.rpb.reservation.reservation.persistence.repository.ReservationJpaRepository;
import com.rpb.reservation.reservation.persistence.repository.ReservationTodayViewProjection;
import com.rpb.reservation.reservation.value.ReservationCode;
import com.rpb.reservation.reservation.value.ReservationId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationPersistenceAdapter implements ReservationRepositoryPort {

    private final ReservationJpaRepository repository;
    private final ReservationMapper mapper;
    private final EntityManager entityManager;

    public ReservationPersistenceAdapter(
        ReservationJpaRepository repository,
        ReservationMapper mapper,
        EntityManager entityManager
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Reservation> findById(StoreScope scope, ReservationId reservationId) {
        return repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            reservationId.value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<Reservation> findByCode(StoreScope scope, ReservationCode reservationCode) {
        return repository.findByTenantIdAndStoreIdAndReservationCodeAndDeletedAtIsNull(
            scope.tenantId().value(),
            scope.storeId().value(),
            reservationCode.value()
        ).map(mapper::toDomain);
    }

    @Override
    public boolean existsByReservationCode(StoreScope scope, ReservationCode reservationCode) {
        return repository.existsByTenantIdAndStoreIdAndReservationCodeAndDeletedAtIsNull(
            scope.tenantId().value(),
            scope.storeId().value(),
            reservationCode.value()
        );
    }

    @Override
    public List<Reservation> findStoreSchedule(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
        return repository.findOverlappingSchedule(
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate.value(),
            toUtc(timeRange.start()),
            toUtc(timeRange.end())
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsActiveDuplicate(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
        if (customerId == null) {
            return false;
        }
        return repository.existsActiveDuplicate(
            scope.tenantId().value(),
            scope.storeId().value(),
            customerId.value(),
            toUtc(timeRange.start()),
            toUtc(timeRange.end())
        );
    }

    @Override
    public List<Reservation> findActiveConflicts(StoreScope scope, CustomerId customerId, TimeRange timeRange) {
        if (customerId == null) {
            return List.of();
        }
        return repository.findActiveConflicts(
            scope.tenantId().value(),
            scope.storeId().value(),
            customerId.value(),
            toUtc(timeRange.start()),
            toUtc(timeRange.end())
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public int findActiveCapacityUsage(StoreScope scope, BusinessDate businessDate, TimeRange timeRange) {
        Long usage = repository.sumActiveOverlappingPartySize(
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate.value(),
            toUtc(timeRange.start()),
            toUtc(timeRange.end())
        );
        return Math.toIntExact(usage == null ? 0L : usage);
    }

    @Override
    public List<ReservationTodayViewRow> findTodayView(
        StoreScope scope,
        BusinessDate businessDate,
        Set<String> statuses
    ) {
        return repository.findTodayView(
            scope.tenantId().value(),
            scope.storeId().value(),
            businessDate.value(),
            statuses
        ).stream().map(ReservationPersistenceAdapter::toTodayViewRow).toList();
    }

    @Override
    public Reservation save(StoreScope scope, Reservation reservation) {
        try {
            ReservationEntity entity = mapper.toEntity(reservation);
            Optional<ReservationEntity> existing = repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
                reservation.id().value(),
                scope.tenantId().value(),
                scope.storeId().value()
            );
            if (existing.isPresent()) {
                return mapper.toDomain(repository.save(existingEntity(entity, existing.get())));
            }
            ReservationEntity newEntity = newEntity(entity);
            entityManager.persist(newEntity);
            entityManager.flush();
            return mapper.toDomain(newEntity);
        } catch (DataIntegrityViolationException | PersistenceException exception) {
            throw new IllegalStateException("reservation_persistence_write_failed", exception);
        }
    }

    private static ReservationEntity newEntity(ReservationEntity entity) {
        return ReservationEntity.of(
            entity.getId(),
            entity.getTenantId(),
            entity.getStoreId(),
            entity.getCustomerId(),
            entity.getReservationCode(),
            entity.getPartySize(),
            entity.getBusinessDate(),
            entity.getReservedStartAt(),
            entity.getReservedEndAt(),
            entity.getHoldUntilAt(),
            entity.getStatus(),
            entity.getSourceChannel(),
            entity.getCancellationReasonCode(),
            entity.getNoShowReasonCode(),
            entity.getNote(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt(),
            null
        );
    }

    private static ReservationEntity existingEntity(ReservationEntity mapped, ReservationEntity existing) {
        return ReservationEntity.of(
            mapped.getId(),
            mapped.getTenantId(),
            mapped.getStoreId(),
            mapped.getCustomerId(),
            mapped.getReservationCode(),
            mapped.getPartySize(),
            mapped.getBusinessDate(),
            mapped.getReservedStartAt(),
            mapped.getReservedEndAt(),
            mapped.getHoldUntilAt(),
            mapped.getStatus(),
            mapped.getSourceChannel(),
            mapped.getCancellationReasonCode(),
            mapped.getNoShowReasonCode(),
            mapped.getNote(),
            existing.getCreatedAt(),
            mapped.getUpdatedAt(),
            existing.getDeletedAt(),
            existing.getVersion()
        );
    }

    private static OffsetDateTime toUtc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static ReservationTodayViewRow toTodayViewRow(ReservationTodayViewProjection projection) {
        return new ReservationTodayViewRow(
            projection.getReservationId(),
            projection.getReservationCode(),
            projection.getStatus(),
            projection.getPartySize() == null ? 0 : projection.getPartySize(),
            toInstant(projection.getReservedStartAt()),
            toInstant(projection.getReservedEndAt()),
            toInstant(projection.getHoldUntilAt()),
            projection.getBusinessDate(),
            projection.getCustomerName(),
            projection.getCustomerNickname(),
            projection.getPhoneE164(),
            projection.getNote()
        );
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
