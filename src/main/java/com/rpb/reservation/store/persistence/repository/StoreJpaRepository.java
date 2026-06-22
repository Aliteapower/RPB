package com.rpb.reservation.store.persistence.repository;

import com.rpb.reservation.store.persistence.entity.StoreEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreJpaRepository extends JpaRepository<StoreEntity, UUID> {

    Optional<StoreEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
