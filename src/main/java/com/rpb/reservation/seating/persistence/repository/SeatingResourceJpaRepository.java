package com.rpb.reservation.seating.persistence.repository;

import com.rpb.reservation.seating.persistence.entity.SeatingResourceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatingResourceJpaRepository extends JpaRepository<SeatingResourceEntity, UUID> {

    Optional<SeatingResourceEntity> findFirstByTenantIdAndStoreIdAndSeatingIdAndStatusAndDeletedAtIsNullOrderByAssignedAtDesc(
        UUID tenantId,
        UUID storeId,
        UUID seatingId,
        String status
    );

    @Query(value = """
        select count(*) > 0
        from seating_resources resource
        join seatings seating
          on seating.id = resource.seating_id
         and seating.tenant_id = resource.tenant_id
         and seating.store_id = resource.store_id
        where resource.tenant_id = :tenantId
          and resource.store_id = :storeId
          and resource.status = 'active'
          and resource.deleted_at is null
          and seating.status = 'occupied'
          and seating.deleted_at is null
          and (
              (:resourceType = 'dining_table' and resource.table_id = :resourceId)
              or (:resourceType = 'table_group' and resource.table_group_id = :resourceId)
          )
        """, nativeQuery = true)
    boolean existsActiveResourceOccupancy(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId
    );

    @Query(value = """
        select *
        from seating_resources
        where tenant_id = :tenantId
          and store_id = :storeId
          and status = 'active'
          and deleted_at is null
          and exists (
              select 1
              from seatings seating
              where seating.id = seating_resources.seating_id
                and seating.tenant_id = seating_resources.tenant_id
                and seating.store_id = seating_resources.store_id
                and seating.status = 'occupied'
                and seating.deleted_at is null
          )
          and (
              (:resourceType = 'dining_table' and table_id = :resourceId)
              or (:resourceType = 'table_group' and table_group_id = :resourceId)
          )
        order by assigned_at desc
        limit 1
        """, nativeQuery = true)
    Optional<SeatingResourceEntity> findActiveOccupancyResource(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId
    );
}
