package com.rpb.reservation.table.persistence.repository;

import com.rpb.reservation.table.persistence.entity.TableGroupEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableGroupJpaRepository extends JpaRepository<TableGroupEntity, UUID> {

    Optional<TableGroupEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

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
          and (:status is null or tableGroup.status = :status)
          and (:partySize is null or (
            tableGroup.capacityMin <= :partySize
            and tableGroup.capacityMax >= :partySize
          ))
        order by tableGroup.groupCode asc
        """)
    List<TableGroupEntity> findVisibleGroups(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("partySize") Integer partySize
    );
}
