package com.rpb.reservation.walkin.persistence.repository;

import com.rpb.reservation.walkin.persistence.entity.WalkInEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkInJpaRepository extends JpaRepository<WalkInEntity, UUID> {

    Optional<WalkInEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    Optional<WalkInEntity> findByTenantIdAndStoreIdAndWalkInCodeAndDeletedAtIsNull(
        UUID tenantId,
        UUID storeId,
        String walkInCode
    );

    List<WalkInEntity> findByTenantIdAndStoreIdAndBusinessDateAndStatusAndDeletedAtIsNullOrderByArrivedAtAsc(
        UUID tenantId,
        UUID storeId,
        LocalDate businessDate,
        String status
    );
}
