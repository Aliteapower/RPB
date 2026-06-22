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
}
