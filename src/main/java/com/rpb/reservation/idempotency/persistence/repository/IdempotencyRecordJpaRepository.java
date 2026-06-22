package com.rpb.reservation.idempotency.persistence.repository;

import com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordJpaRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {

    Optional<IdempotencyRecordEntity> findByTenantIdAndStoreIdAndSourceAndActionAndIdempotencyKey(
        UUID tenantId,
        UUID storeId,
        String source,
        String action,
        String idempotencyKey
    );

    Optional<IdempotencyRecordEntity> findByTenantIdAndStoreIdIsNullAndSourceAndActionAndIdempotencyKey(
        UUID tenantId,
        String source,
        String action,
        String idempotencyKey
    );

    Optional<IdempotencyRecordEntity> findByTenantIdIsNullAndStoreIdIsNullAndSourceAndActionAndIdempotencyKey(
        String source,
        String action,
        String idempotencyKey
    );
}
