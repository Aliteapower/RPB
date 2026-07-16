package com.rpb.reservation.table.persistence.repository;

import com.rpb.reservation.table.persistence.entity.DiningTableEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiningTableJpaRepository extends JpaRepository<DiningTableEntity, UUID> {

    Optional<DiningTableEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select diningTable from DiningTableEntity diningTable
        where diningTable.id = :tableId
          and diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
        """)
    Optional<DiningTableEntity> findForUpdate(
        @Param("tableId") UUID tableId,
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId
    );

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
        order by diningTable.tableCode asc
        """)
    List<DiningTableEntity> findVisibleResourcesWithoutFilters(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId
    );

    @Query("""
        select diningTable from DiningTableEntity diningTable
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and diningTable.status = :status
        order by diningTable.tableCode asc
        """)
    List<DiningTableEntity> findVisibleResourcesByStatus(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status
    );

    @Query("""
        select diningTable from DiningTableEntity diningTable
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and diningTable.capacityMin <= :partySize
          and diningTable.capacityMax >= :partySize
        order by diningTable.tableCode asc
        """)
    List<DiningTableEntity> findVisibleResourcesByPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("partySize") int partySize
    );

    @Query("""
        select diningTable from DiningTableEntity diningTable
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and diningTable.status = :status
          and diningTable.capacityMin <= :partySize
          and diningTable.capacityMax >= :partySize
        order by diningTable.tableCode asc
        """)
    List<DiningTableEntity> findVisibleResourcesByStatusAndPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("partySize") int partySize
    );

    @Query("""
        select diningTable.id as resourceId,
               diningTable.tableCode as code,
               diningTable.displayName as displayName,
               area.displayName as areaName,
               diningTable.capacityMin as capacityMin,
               diningTable.capacityMax as capacityMax,
               diningTable.status as status
        from DiningTableEntity diningTable
        join StoreAreaEntity area
          on area.id = diningTable.areaId
         and area.tenantId = diningTable.tenantId
         and area.storeId = diningTable.storeId
         and area.deletedAt is null
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
        order by area.sortOrder asc, diningTable.tableCode asc
        """)
    List<DiningTableResourceProjection> findVisibleResourceRowsWithoutFilters(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId
    );

    @Query("""
        select diningTable.id as resourceId,
               diningTable.tableCode as code,
               diningTable.displayName as displayName,
               area.displayName as areaName,
               diningTable.capacityMin as capacityMin,
               diningTable.capacityMax as capacityMax,
               diningTable.status as status
        from DiningTableEntity diningTable
        join StoreAreaEntity area
          on area.id = diningTable.areaId
         and area.tenantId = diningTable.tenantId
         and area.storeId = diningTable.storeId
         and area.deletedAt is null
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and diningTable.status = :status
        order by area.sortOrder asc, diningTable.tableCode asc
        """)
    List<DiningTableResourceProjection> findVisibleResourceRowsByStatus(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status
    );

    @Query("""
        select diningTable.id as resourceId,
               diningTable.tableCode as code,
               diningTable.displayName as displayName,
               area.displayName as areaName,
               diningTable.capacityMin as capacityMin,
               diningTable.capacityMax as capacityMax,
               diningTable.status as status
        from DiningTableEntity diningTable
        join StoreAreaEntity area
          on area.id = diningTable.areaId
         and area.tenantId = diningTable.tenantId
         and area.storeId = diningTable.storeId
         and area.deletedAt is null
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and diningTable.capacityMin <= :partySize
          and diningTable.capacityMax >= :partySize
        order by area.sortOrder asc, diningTable.tableCode asc
        """)
    List<DiningTableResourceProjection> findVisibleResourceRowsByPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("partySize") int partySize
    );

    @Query("""
        select diningTable.id as resourceId,
               diningTable.tableCode as code,
               diningTable.displayName as displayName,
               area.displayName as areaName,
               diningTable.capacityMin as capacityMin,
               diningTable.capacityMax as capacityMax,
               diningTable.status as status
        from DiningTableEntity diningTable
        join StoreAreaEntity area
          on area.id = diningTable.areaId
         and area.tenantId = diningTable.tenantId
         and area.storeId = diningTable.storeId
         and area.deletedAt is null
        where diningTable.tenantId = :tenantId
          and diningTable.storeId = :storeId
          and diningTable.deletedAt is null
          and diningTable.status = :status
          and diningTable.capacityMin <= :partySize
          and diningTable.capacityMax >= :partySize
        order by area.sortOrder asc, diningTable.tableCode asc
        """)
    List<DiningTableResourceProjection> findVisibleResourceRowsByStatusAndPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("partySize") int partySize
    );
}
