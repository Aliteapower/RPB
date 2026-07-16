package com.rpb.reservation.table.persistence.repository;

import com.rpb.reservation.table.persistence.entity.TableGroupEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableGroupJpaRepository extends JpaRepository<TableGroupEntity, UUID> {

    Optional<TableGroupEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    boolean existsByTenantIdAndStoreIdAndGroupCodeAndDeletedAtIsNull(UUID tenantId, UUID storeId, String groupCode);

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and tableGroup.status = 'active'
          and tableGroup.capacityMin <= :partySize
          and tableGroup.capacityMax >= :partySize
        order by tableGroup.capacityMax asc, tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findAvailableCandidates(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("partySize") int partySize
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsWithoutFilters(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and tableGroup.status = :status
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsByStatus(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and tableGroup.capacityMin <= :partySize
          and tableGroup.capacityMax >= :partySize
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsByPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("partySize") int partySize
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and tableGroup.status = :status
          and tableGroup.capacityMin <= :partySize
          and tableGroup.capacityMax >= :partySize
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsByStatusAndPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("partySize") int partySize
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and (
            tableGroup.groupType <> 'temporary'
            or (
              tableGroup.activeFromAt is not null
              and tableGroup.activeFromAt < :businessEndAt
              and (tableGroup.activeUntilAt is null or tableGroup.activeUntilAt > :businessStartAt)
            )
            or (
              tableGroup.activeFromAt is null
              and tableGroup.createdAt >= :businessStartAt
              and tableGroup.createdAt < :businessEndAt
            )
          )
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsWithoutFiltersForBusinessWindow(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessStartAt") OffsetDateTime businessStartAt,
        @Param("businessEndAt") OffsetDateTime businessEndAt
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and tableGroup.status = :status
          and (
            tableGroup.groupType <> 'temporary'
            or (
              tableGroup.activeFromAt is not null
              and tableGroup.activeFromAt < :businessEndAt
              and (tableGroup.activeUntilAt is null or tableGroup.activeUntilAt > :businessStartAt)
            )
            or (
              tableGroup.activeFromAt is null
              and tableGroup.createdAt >= :businessStartAt
              and tableGroup.createdAt < :businessEndAt
            )
          )
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsByStatusForBusinessWindow(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("businessStartAt") OffsetDateTime businessStartAt,
        @Param("businessEndAt") OffsetDateTime businessEndAt
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and tableGroup.capacityMin <= :partySize
          and tableGroup.capacityMax >= :partySize
          and (
            tableGroup.groupType <> 'temporary'
            or (
              tableGroup.activeFromAt is not null
              and tableGroup.activeFromAt < :businessEndAt
              and (tableGroup.activeUntilAt is null or tableGroup.activeUntilAt > :businessStartAt)
            )
            or (
              tableGroup.activeFromAt is null
              and tableGroup.createdAt >= :businessStartAt
              and tableGroup.createdAt < :businessEndAt
            )
          )
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsByPartySizeForBusinessWindow(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("partySize") int partySize,
        @Param("businessStartAt") OffsetDateTime businessStartAt,
        @Param("businessEndAt") OffsetDateTime businessEndAt
    );

    @Query("""
        select tableGroup from TableGroupEntity tableGroup
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.deletedAt is null
          and tableGroup.status = :status
          and tableGroup.capacityMin <= :partySize
          and tableGroup.capacityMax >= :partySize
          and (
            tableGroup.groupType <> 'temporary'
            or (
              tableGroup.activeFromAt is not null
              and tableGroup.activeFromAt < :businessEndAt
              and (tableGroup.activeUntilAt is null or tableGroup.activeUntilAt > :businessStartAt)
            )
            or (
              tableGroup.activeFromAt is null
              and tableGroup.createdAt >= :businessStartAt
              and tableGroup.createdAt < :businessEndAt
            )
          )
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroupsByStatusAndPartySizeForBusinessWindow(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("partySize") int partySize,
        @Param("businessStartAt") OffsetDateTime businessStartAt,
        @Param("businessEndAt") OffsetDateTime businessEndAt
    );

    @Modifying
    @Query("""
        update TableGroupEntity tableGroup
        set tableGroup.deletedAt = :deletedAt,
            tableGroup.updatedAt = :deletedAt
        where tableGroup.tenantId = :tenantId
          and tableGroup.storeId = :storeId
          and tableGroup.id = :tableGroupId
          and tableGroup.deletedAt is null
        """)
    int softDeleteGroup(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("tableGroupId") UUID tableGroupId,
        @Param("deletedAt") java.time.OffsetDateTime deletedAt
    );
}
