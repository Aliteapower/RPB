package com.rpb.reservation.cleaning.persistence.repository;

import com.rpb.reservation.cleaning.persistence.entity.CleaningEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CleaningJpaRepository extends JpaRepository<CleaningEntity, UUID> {

    Optional<CleaningEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    Optional<CleaningEntity> findFirstByTenantIdAndStoreIdAndSeatingIdAndDeletedAtIsNullOrderByStartedAtDesc(
        UUID tenantId,
        UUID storeId,
        UUID seatingId
    );

    @Query(value = """
        select *
        from cleanings
        where tenant_id = :tenantId
          and store_id = :storeId
          and deleted_at is null
          and status in ('pending', 'cleaning', 'completed')
          and (
              (:resourceType = 'dining_table' and table_id = :resourceId)
              or (:resourceType = 'table_group' and table_group_id = :resourceId)
          )
        order by started_at desc nulls last, created_at desc
        limit 1
        """, nativeQuery = true)
    Optional<CleaningEntity> findActiveByResource(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId
    );
}
