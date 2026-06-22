package com.rpb.reservation.audit.persistence.repository;

import com.rpb.reservation.audit.persistence.entity.BusinessEventEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessEventJpaRepository extends JpaRepository<BusinessEventEntity, UUID> {

    List<BusinessEventEntity> findByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtAsc(
        UUID tenantId,
        UUID storeId,
        String targetType,
        UUID targetId
    );

    List<BusinessEventEntity> findByTenantIdAndStoreIdAndOccurredAtBetweenOrderByOccurredAtAsc(
        UUID tenantId,
        UUID storeId,
        OffsetDateTime start,
        OffsetDateTime end
    );
}
