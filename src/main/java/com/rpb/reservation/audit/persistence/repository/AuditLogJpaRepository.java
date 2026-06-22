package com.rpb.reservation.audit.persistence.repository;

import com.rpb.reservation.audit.persistence.entity.AuditLogEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, UUID> {

    List<AuditLogEntity> findByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtAsc(
        UUID tenantId,
        UUID storeId,
        String targetType,
        UUID targetId
    );

    List<AuditLogEntity> findByTenantIdAndStoreIdAndOperationCodeAndOccurredAtBetweenOrderByOccurredAtAsc(
        UUID tenantId,
        UUID storeId,
        String operationCode,
        OffsetDateTime start,
        OffsetDateTime end
    );
}
