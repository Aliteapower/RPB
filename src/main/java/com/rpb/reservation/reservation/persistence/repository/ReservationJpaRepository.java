package com.rpb.reservation.reservation.persistence.repository;

import com.rpb.reservation.reservation.persistence.entity.ReservationEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, UUID> {

    Optional<ReservationEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
        UUID id,
        UUID tenantId,
        UUID storeId
    );

    Optional<ReservationEntity> findByTenantIdAndStoreIdAndReservationCodeAndDeletedAtIsNull(
        UUID tenantId,
        UUID storeId,
        String reservationCode
    );

    boolean existsByTenantIdAndStoreIdAndReservationCodeAndDeletedAtIsNull(
        UUID tenantId,
        UUID storeId,
        String reservationCode
    );

    @Query(value = """
        select exists (
            select 1
            from reservations
            where tenant_id = :tenantId
              and store_id = :storeId
              and customer_id = :customerId
              and status in ('confirmed', 'arrived', 'seated')
              and deleted_at is null
              and reserved_start_at < :requestedEnd
              and reserved_end_at > :requestedStart
        )
        """, nativeQuery = true)
    boolean existsActiveDuplicate(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("customerId") UUID customerId,
        @Param("requestedStart") OffsetDateTime requestedStart,
        @Param("requestedEnd") OffsetDateTime requestedEnd
    );

    @Query(value = """
        select coalesce(sum(party_size), 0)
        from reservations
        where tenant_id = :tenantId
          and store_id = :storeId
          and business_date = :businessDate
          and status in ('confirmed', 'arrived', 'seated')
          and deleted_at is null
          and reserved_start_at < :requestedEnd
          and reserved_end_at > :requestedStart
        """, nativeQuery = true)
    Long sumActiveOverlappingPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate,
        @Param("requestedStart") OffsetDateTime requestedStart,
        @Param("requestedEnd") OffsetDateTime requestedEnd
    );

    @Query(value = """
        select *
        from reservations
        where tenant_id = :tenantId
          and store_id = :storeId
          and business_date = :businessDate
          and deleted_at is null
          and reserved_start_at < :requestedEnd
          and reserved_end_at > :requestedStart
        order by reserved_start_at asc, created_at asc
        """, nativeQuery = true)
    List<ReservationEntity> findOverlappingSchedule(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate,
        @Param("requestedStart") OffsetDateTime requestedStart,
        @Param("requestedEnd") OffsetDateTime requestedEnd
    );

    @Query(value = """
        select *
        from reservations
        where tenant_id = :tenantId
          and store_id = :storeId
          and customer_id = :customerId
          and status in ('confirmed', 'arrived', 'seated')
          and deleted_at is null
          and reserved_start_at < :requestedEnd
          and reserved_end_at > :requestedStart
        order by reserved_start_at asc, created_at asc
        """, nativeQuery = true)
    List<ReservationEntity> findActiveConflicts(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("customerId") UUID customerId,
        @Param("requestedStart") OffsetDateTime requestedStart,
        @Param("requestedEnd") OffsetDateTime requestedEnd
    );

    @Query("""
        select
          r.id as reservationId,
          r.reservationCode as reservationCode,
          r.status as status,
          r.partySize as partySize,
          r.reservedStartAt as reservedStartAt,
          r.reservedEndAt as reservedEndAt,
          r.holdUntilAt as holdUntilAt,
          r.businessDate as businessDate,
          c.displayName as customerName,
          c.nickname as customerNickname,
          c.phoneE164 as phoneE164,
          r.note as note
        from ReservationEntity r
          left join CustomerEntity c
            on c.tenantId = r.tenantId
           and c.id = r.customerId
           and c.deletedAt is null
        where r.tenantId = :tenantId
          and r.storeId = :storeId
          and r.businessDate = :businessDate
          and r.status in :statuses
          and r.deletedAt is null
        order by r.reservedStartAt asc, r.createdAt asc
        """)
    List<ReservationTodayViewProjection> findTodayView(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate,
        @Param("statuses") Collection<String> statuses
    );

    @Query("""
        select
          r.businessDate as businessDate,
          count(r.id) as reservationCount
        from ReservationEntity r
        where r.tenantId = :tenantId
          and r.storeId = :storeId
          and r.businessDate >= :startInclusive
          and r.businessDate < :endExclusive
          and r.status in :statuses
          and r.deletedAt is null
        group by r.businessDate
        order by r.businessDate asc
        """)
    List<ReservationCalendarSummaryProjection> findCalendarSummary(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("startInclusive") LocalDate startInclusive,
        @Param("endExclusive") LocalDate endExclusive,
        @Param("statuses") Collection<String> statuses
    );
}
