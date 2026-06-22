package com.rpb.reservation.store.persistence.repository;

import com.rpb.reservation.store.persistence.entity.StorePolicyEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorePolicyJpaRepository extends JpaRepository<StorePolicyEntity, UUID> {

    @Query(value = """
        select *
        from store_policies
        where tenant_id = :tenantId
          and store_id = :storeId
          and deleted_at is null
          and effective_from_at <= :at
          and (effective_to_at is null or effective_to_at > :at)
        order by effective_from_at desc
        limit 1
        """, nativeQuery = true)
    Optional<StorePolicyEntity> findCurrentPolicy(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("at") OffsetDateTime at
    );
}
