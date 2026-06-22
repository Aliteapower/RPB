package com.rpb.reservation.table.persistence.repository;

import com.rpb.reservation.table.persistence.entity.TableGroupMemberEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
