package com.rpb.reservation.table.persistence.repository;

import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiningTableJpaRepository extends JpaRepository<DiningTableEntity, UUID> {

    Optional<DiningTableEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    List<DiningTableEntity> findByTenantIdAndStoreIdAndAreaIdAndDeletedAtIsNull(UUID tenantId, UUID storeId, UUID areaId);

    @Query("""
        select diningTable from DiningTableEntity diningTable
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and diningTable.status = 'available'
          and diningTable.capacityMin <= :partySize
          and diningTable.capacityMax >= :partySize
        order by diningTable.capacityMax asc, diningTable.tableCode asc
        """)
    List<DiningTableEntity> findAvailableCandidates(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("partySize") int partySize
    );

    @Query("""
        select diningTable from DiningTableEntity diningTable
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and (:status is null or diningTable.status = :status)
          and (:partySize is null or (
            diningTable.capacityMin <= :partySize
            and diningTable.capacityMax >= :partySize
          ))
        order by diningTable.tableCode asc
        """)
    List<DiningTableEntity> findVisibleResources(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("partySize") Integer partySize
    );
}
