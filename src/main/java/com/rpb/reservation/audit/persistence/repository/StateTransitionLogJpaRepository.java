package com.rpb.reservation.audit.persistence.repository;

import com.rpb.reservation.audit.persistence.entity.StateTransitionLogEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateTransitionLogJpaRepository extends JpaRepository<StateTransitionLogEntity, UUID> {

    List<StateTransitionLogEntity> findByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtAsc(
        UUID tenantId,
        UUID storeId,
        String targetType,
        UUID targetId
    );

    Optional<StateTransitionLogEntity> findFirstByTenantIdAndStoreIdAndTargetTypeAndTargetIdOrderByOccurredAtDesc(
        UUID tenantId,
        UUID storeId,
        String targetType,
        UUID targetId
    );
}
