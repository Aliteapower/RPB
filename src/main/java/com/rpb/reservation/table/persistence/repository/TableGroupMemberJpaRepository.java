package com.rpb.reservation.table.persistence.repository;

import com.rpb.reservation.table.persistence.entity.TableGroupMemberEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableGroupMemberJpaRepository extends JpaRepository<TableGroupMemberEntity, UUID> {

    List<TableGroupMemberEntity> findByTenantIdAndStoreIdAndTableGroupIdAndDeletedAtIsNull(
        UUID tenantId,
        UUID storeId,
        UUID tableGroupId
    );

    @Query("""
        select member from TableGroupMemberEntity member
        where member.tenantId = :tenantId
          and member.storeId = :storeId
          and member.tableId = :tableId
          and member.deletedAt is null
        """)
    List<TableGroupMemberEntity> findActiveMembersForTable(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("tableId") UUID tableId
    );

    @Query("""
        select member from TableGroupMemberEntity member
        join TableGroupEntity tableGroup
          on tableGroup.id = member.tableGroupId
         and tableGroup.tenantId = member.tenantId
         and tableGroup.storeId = member.storeId
         and tableGroup.deletedAt is null
        where member.tenantId = :tenantId
          and member.storeId = :storeId
          and member.tableId = :tableId
          and member.deletedAt is null
          and tableGroup.groupType = 'temporary'
          and tableGroup.status in ('created', 'locked', 'occupied')
        """)
    List<TableGroupMemberEntity> findActiveTemporaryMembersForTable(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("tableId") UUID tableId
    );

    @Query("""
        select member from TableGroupMemberEntity member
        join TableGroupEntity tableGroup
          on tableGroup.id = member.tableGroupId
         and tableGroup.tenantId = member.tenantId
         and tableGroup.storeId = member.storeId
         and tableGroup.deletedAt is null
        where member.tenantId = :tenantId
          and member.storeId = :storeId
          and member.tableId = :tableId
          and member.deletedAt is null
          and tableGroup.groupType = 'temporary'
          and tableGroup.status in ('created', 'locked', 'occupied')
          and (
            (
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
        """)
    List<TableGroupMemberEntity> findActiveTemporaryMembersForTableInBusinessWindow(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("tableId") UUID tableId,
        @Param("businessStartAt") OffsetDateTime businessStartAt,
        @Param("businessEndAt") OffsetDateTime businessEndAt
    );

    @Modifying
    @Query("""
        update TableGroupMemberEntity member
        set member.deletedAt = :deletedAt
        where member.tenantId = :tenantId
          and member.storeId = :storeId
          and member.tableGroupId = :tableGroupId
          and member.deletedAt is null
        """)
    int softDeleteMembersForGroup(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("tableGroupId") UUID tableGroupId,
        @Param("deletedAt") java.time.OffsetDateTime deletedAt
    );
}
