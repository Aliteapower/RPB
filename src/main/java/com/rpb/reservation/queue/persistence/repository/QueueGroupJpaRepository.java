package com.rpb.reservation.queue.persistence.repository;

import com.rpb.reservation.queue.persistence.entity.QueueGroupEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueGroupJpaRepository extends JpaRepository<QueueGroupEntity, UUID> {

    Optional<QueueGroupEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    Optional<QueueGroupEntity> findByTenantIdAndStoreIdAndGroupCodeAndStatusAndDeletedAtIsNull(
        UUID tenantId,
        UUID storeId,
        String groupCode,
        String status
    );

    @Query(value = """
        select *
        from queue_groups
        where tenant_id = :tenantId
          and store_id = :storeId
          and status = 'active'
          and deleted_at is null
          and min_party_size <= :partySize
          and (max_party_size is null or max_party_size >= :partySize)
        order by sort_order asc, group_code asc
        limit 1
        """, nativeQuery = true)
    Optional<QueueGroupEntity> findActiveByPartySize(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("partySize") int partySize
    );
}
