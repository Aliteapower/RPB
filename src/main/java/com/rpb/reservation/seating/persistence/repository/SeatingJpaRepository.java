package com.rpb.reservation.seating.persistence.repository;

import com.rpb.reservation.seating.persistence.entity.SeatingEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatingJpaRepository extends JpaRepository<SeatingEntity, UUID> {

    Optional<SeatingEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    @Query("""
        select seating from SeatingEntity seating
        where seating.tenantId = :tenantId
          and seating.storeId = :storeId
          and seating.deletedAt is null
          and seating.status in ('planned', 'locked', 'occupied')
          and (
              (:sourceType = 'reservation' and seating.reservationId = :sourceId)
              or (:sourceType = 'queue_ticket' and seating.queueTicketId = :sourceId)
              or (:sourceType = 'walk_in' and seating.walkInId = :sourceId)
          )
        """)
    Optional<SeatingEntity> findActiveBySource(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("sourceType") String sourceType,
        @Param("sourceId") UUID sourceId
    );

    @Query(value = """
        select s.*
        from seatings s
        where s.tenant_id = :tenantId
          and s.store_id = :storeId
          and s.deleted_at is null
          and s.status in ('occupied', 'completed', 'cleaning_triggered')
          and (
              s.reservation_id = :reservationId
              or s.queue_ticket_id in (
                  select qt.id
                  from queue_tickets qt
                  where qt.tenant_id = s.tenant_id
                    and qt.store_id = s.store_id
                    and qt.reservation_id = :reservationId
                    and qt.deleted_at is null
              )
          )
        order by s.updated_at desc
        limit 1
        """, nativeQuery = true)
    Optional<SeatingEntity> findCurrentByReservation(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("reservationId") UUID reservationId
    );
}
