package com.rpb.reservation.table.persistence.repository;

import com.rpb.reservation.table.persistence.entity.TableLockEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableLockJpaRepository extends JpaRepository<TableLockEntity, UUID> {

    @Query(value = """
        select *
        from table_locks
        where tenant_id = :tenantId
          and store_id = :storeId
          and resource_type = :resourceType
          and resource_id = :resourceId
          and status = 'active'
          and locked_until_at > current_timestamp
        order by locked_until_at desc
        limit 1
        """, nativeQuery = true)
    Optional<TableLockEntity> findActiveByResource(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId
    );

    @Query("""
        select count(tableLock) > 0 from TableLockEntity tableLock
        where tableLock.tenantId = :tenantId
          and tableLock.storeId = :storeId
          and tableLock.resourceType = :resourceType
          and tableLock.resourceId = :resourceId
          and tableLock.status = 'active'
          and tableLock.lockedUntilAt > :at
        """)
    boolean existsActiveConflict(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId,
        @Param("at") OffsetDateTime at
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update TableLockEntity tableLock
        set tableLock.status = 'expired',
            tableLock.releasedAt = :expiredAt,
            tableLock.updatedAt = :expiredAt
        where tableLock.tenantId = :tenantId
          and tableLock.storeId = :storeId
          and tableLock.resourceType = :resourceType
          and tableLock.resourceId = :resourceId
          and tableLock.status = 'active'
          and tableLock.lockedUntilAt <= :expiredAt
        """)
    int expireActiveLocksForResourceBefore(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId,
        @Param("expiredAt") OffsetDateTime expiredAt
    );

    Optional<TableLockEntity> findByIdAndTenantIdAndStoreId(UUID id, UUID tenantId, UUID storeId);
}
