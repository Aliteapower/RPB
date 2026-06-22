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

    @Query("""
        select count(resource) > 0 from SeatingResourceEntity resource
        where resource.tenantId = :tenantId
          and resource.storeId = :storeId
          and resource.status = 'active'
          and resource.deletedAt is null
          and (
              (:resourceType = 'dining_table' and resource.tableId = :resourceId)
              or (:resourceType = 'table_group' and resource.tableGroupId = :resourceId)
          )
        """)
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
